package libbox

// AURA's isolated probes never install a TUN or fall back to a direct HTTP request.
import (
 "context"
 "crypto/rand"
 "encoding/json"
 "errors"
 "fmt"
 "io"
 "net"
 "net/http"
 "net/http/httptrace"
 "net/url"
 "os"
 "time"

 box "github.com/sagernet/sing-box"
 "github.com/sagernet/sing-box/include"
 M "github.com/sagernet/sing/common/metadata"
 "golang.org/x/net/icmp"
 "golang.org/x/net/ipv4"
)

type auraProbeResult struct {
 MS int64 `json:"ms"`
 Status int `json:"status"`
 Attempts int `json:"attempts"`
 Reused bool `json:"reused"`
}

// AuraProbe returns a JSON result. TCP/ICMP test the host; GET/HEAD use proxy.
func AuraProbe(config, host string, port int32, target, method, mode string, timeoutMS int32) (string, error) {
 if timeoutMS < 1000 || timeoutMS > 15000 { return "", errors.New("invalid timeout") }
 ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeoutMS)*time.Millisecond)
 defer cancel()
 start := time.Now()
 result := auraProbeResult{Attempts:1}
 switch method {
 case "TCP":
  if port < 1 || port > 65535 { return "", errors.New("invalid port") }
  conn, err := (&net.Dialer{}).DialContext(ctx,"tcp",net.JoinHostPort(host,fmt.Sprint(port)))
  if err != nil { return "",err }; conn.Close()
 case "ICMP":
  addresses, err := net.DefaultResolver.LookupIP(ctx,"ip4",host)
  if err != nil { return "",err }; if len(addresses)==0 { return "",errors.New("no IPv4 address for ICMP") }
  conn,err := icmp.ListenPacket("udp4","0.0.0.0")
  if err != nil { return "",fmt.Errorf("ICMP unavailable: %w",err) }; defer conn.Close()
  deadline,_:=ctx.Deadline(); conn.SetDeadline(deadline)
  payload:=make([]byte,24); if _,err=rand.Read(payload);err!=nil{return "",err}
  packet,err:=(&icmp.Message{Type:ipv4.ICMPTypeEcho,Code:0,Body:&icmp.Echo{ID:os.Getpid()&0xffff,Seq:1,Data:payload}}).Marshal(nil)
  if err!=nil{return "",err}
  if _,err=conn.WriteTo(packet,&net.UDPAddr{IP:addresses[0]});err!=nil{return "",err}
  buffer:=make([]byte,1500)
  for {
   n,peer,e:=conn.ReadFrom(buffer);if e!=nil{return "",e}
   reply,e:=icmp.ParseMessage(1,buffer[:n]);if e!=nil{continue}
   echo,ok:=reply.Body.(*icmp.Echo)
   if ok && reply.Type==ipv4.ICMPTypeEchoReply && echo.Seq==1 && string(echo.Data)==string(payload) && peer.String()==addresses[0].String()+":0" {break}
   // Ping sockets on Darwin report an IPAddr instead of a UDPAddr.
   if ok && reply.Type==ipv4.ICMPTypeEchoReply && echo.Seq==1 && string(echo.Data)==string(payload) && peer.String()==addresses[0].String() {break}
  }
 case "GET","HEAD":
  u,err:=url.Parse(target)
  if err!=nil || u.Hostname()=="" || (u.Scheme!="http" && u.Scheme!="https") || u.User!=nil {return "",errors.New("invalid test URL")}
  if mode!="default" && mode!="double" && mode!="keepalive" {return "",errors.New("invalid mode")}
  // Strip TUN, routing policies and DNS detours from the supplied VPN config.
  var raw map[string]json.RawMessage
  if err=json.Unmarshal([]byte(config),&raw);err!=nil{return "",err}
  clean:=map[string]any{"outbounds":raw["outbounds"],"log":map[string]any{"disabled":true},"dns":map[string]any{"servers":[]any{map[string]any{"address":"local"}}},"route":map[string]any{"final":"proxy"}}
  encoded,_:=json.Marshal(clean)
  boxCtx:=box.Context(ctx,include.InboundRegistry(),include.OutboundRegistry(),include.EndpointRegistry())
  options,err:=parseConfig(boxCtx,string(encoded));if err!=nil{return "",err}
  instance,err:=box.New(box.Options{Context:boxCtx,Options:options});if err!=nil{return "",err}
  defer instance.Close()
  if err=instance.Start();err!=nil{return "",err}
  outbound,ok:=instance.Outbound().Outbound("proxy");if !ok{return "",errors.New("proxy outbound missing")}
  transport:=&http.Transport{DisableKeepAlives:mode!="keepalive",TLSHandshakeTimeout:time.Duration(timeoutMS)*time.Millisecond,DialContext:func(ctx context.Context, network,address string)(net.Conn,error){return outbound.DialContext(ctx,network,M.ParseSocksaddr(address))}}
  defer transport.CloseIdleConnections()
  client:=&http.Client{Transport:transport,CheckRedirect:func(req *http.Request,via []*http.Request)error{return http.ErrUseLastResponse}}
  count:=1;if mode!="default"{count=2};result.MS=0
  var lastErr error
  for i:=0;i<count;i++ {
   request,err:=http.NewRequestWithContext(ctx,method,target,nil);if err!=nil{return "",err}
   reused:=false
   request=request.WithContext(httptrace.WithClientTrace(request.Context(),&httptrace.ClientTrace{GotConn:func(info httptrace.GotConnInfo){reused=info.Reused}}))
   request.Header.Set("User-Agent","AURA/1.0")
   begin:=time.Now();response,err:=client.Do(request)
   if err!=nil{lastErr=err;continue}
   elapsed:=max(int64(1),time.Since(begin).Milliseconds())
   // Drain small responses to enable reuse, cap arbitrary URL downloads.
   _,drainErr:=io.Copy(io.Discard,io.LimitReader(response.Body,64*1024));response.Body.Close()
   if drainErr!=nil{lastErr=drainErr;continue}
   if result.MS==0 || elapsed<result.MS {result.MS=elapsed;result.Status=response.StatusCode;result.Reused=reused}
   result.Attempts=i+1
  }
  if result.MS==0 {return "",lastErr}
  data,err:=json.Marshal(result);return string(data),err
 default:return "",errors.New("unknown probe method")
 }
 result.MS=max(int64(1),time.Since(start).Milliseconds())
 data,err:=json.Marshal(result);return string(data),err
}
