package app.aura.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VaultConcurrencyTest {
    @Test public void independentVaultInstancesDoNotRollBackAnInFlightWrite()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Vault initial=new Vault(context);JSONObject previous=initial.load();ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
            initial.save(Core.defaults().put("saveRevision",0));CountDownLatch start=new CountDownLatch(1);
            Future<?> reader=pool.submit(()->{try{start.await();for(int i=0;i<80;i++){JSONObject value=new Vault(context).load();assertTrue(value.has("profiles"));assertTrue(value.optInt("saveRevision")>=0);}}catch(Exception e){throw new RuntimeException(e);}});
            Future<?> writer=pool.submit(()->{try{start.await();for(int i=1;i<=25;i++)new Vault(context).save(Core.defaults().put("saveRevision",i));}catch(Exception e){throw new RuntimeException(e);}});
            start.countDown();reader.get(20,TimeUnit.SECONDS);writer.get(20,TimeUnit.SECONDS);assertEquals(25,new Vault(context).load().getInt("saveRevision"));
        }finally{pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);initial.save(previous);}
    }
}
