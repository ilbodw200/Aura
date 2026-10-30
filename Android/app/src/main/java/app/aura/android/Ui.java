package app.aura.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

final class Ui {
    final Context context;final int accent,fg,muted,bg;final boolean light;final int radius;final boolean compact;
    Ui(Context context,int accent,boolean light,int radius,boolean compact){this.context=context;this.accent=accent;this.light=light;this.radius=radius;this.compact=compact;fg=Color.parseColor(light?"#102B33":"#EDF5F5");muted=Color.parseColor(light?"#566A70":"#A0B2BC");bg=Color.parseColor(light?"#EDF3F3":"#07111C");}
    int dp(float value){return (int)(value*context.getResources().getDisplayMetrics().density+.5f);}
    int alpha(int color,int alpha){return (color&0xffffff)|(alpha<<24);}
    GradientDrawable rounded(int color,int stroke,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout line(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String value,int size,int color){TextView v=new TextView(context);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");return v;}
    TextView title(String value,int size){TextView v=text(value,size,fg);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    TextView caption(String value){TextView v=text(value,10,muted);v.setLetterSpacing(.13f);return v;}
    void space(LinearLayout parent,int height){View v=new View(context);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    LinearLayout card(LinearLayout parent){LinearLayout v=column();int pad=dp(compact?15:21);v.setPadding(pad,pad,pad,pad);v.setBackground(rounded(light?0xebffffff:0x0cffffff,light?0x14002a30:0x15ffffff,radius));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(v,p);return v;}
    TextView button(String label,Runnable action){TextView v=title(label,14);v.setTextColor(accent);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(48));v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(rounded(alpha(accent,light?25:20),alpha(accent,45),18));v.setOnClickListener(x->action.run());v.setFocusable(true);v.setContentDescription(label);return v;}
    EditText input(String hint,String value){EditText v=new EditText(context);v.setText(value);v.setHint(hint);v.setTextSize(15);v.setTextColor(fg);v.setHintTextColor(muted);v.setBackgroundTintList(ColorStateList.valueOf(accent));v.setPadding(dp(4),dp(10),dp(4),dp(10));v.setSelectAllOnFocus(false);return v;}
    Switch toggle(String label,boolean checked,java.util.function.Consumer<Boolean> onChange){Switch v=new Switch(context);v.setText(label);v.setTextColor(fg);v.setTextSize(14);v.setMinHeight(dp(52));v.setChecked(checked);v.setThumbTintList(ColorStateList.valueOf(accent));v.setOnCheckedChangeListener((b,value)->onChange.accept(value));return v;}
    Spinner spinner(String[] values,int selected){Spinner s=new Spinner(context);ArrayAdapter<String> a=new ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,values){@Override public View getView(int position,View convert,android.view.ViewGroup parent){TextView v=(TextView)super.getView(position,convert,parent);v.setTextColor(fg);v.setTextSize(14);return v;}};a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(selected);s.setMinimumHeight(dp(48));return s;}
    static final class Orb extends View {
        private final Ui ui;private final boolean active;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Orb(Ui ui,boolean active){super(ui.context);this.ui=ui;this.active=active;setContentDescription(active?"Отключить VPN":"Подключить VPN");setFocusable(true);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.84f;
            p.setShader(new RadialGradient(x,y,r,new int[]{ui.alpha(ui.accent,active?70:40),ui.alpha(ui.accent,0)},null,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,r,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(ui.dp(1));for(int i=0;i<3;i++){p.setColor(ui.alpha(ui.accent,25+i*10));canvas.drawCircle(x,y,r*(1-i*.1f),p);}
            float inner=r*.77f;p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(x-inner,y-inner,x+inner,y+inner,new int[]{ui.alpha(ui.accent,65),ui.alpha(ui.accent,10)},null,Shader.TileMode.CLAMP));canvas.drawCircle(x,y,inner,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(ui.alpha(ui.accent,100));p.setStrokeWidth(ui.dp(1));canvas.drawCircle(x,y,inner,p);
            p.setColor(ui.accent);p.setStrokeWidth(ui.dp(4));p.setStrokeCap(Paint.Cap.ROUND);y-=ui.dp(24);float symbol=r*.20f;canvas.drawArc(new RectF(x-symbol,y-symbol,x+symbol,y+symbol),-45,270,false,p);canvas.drawLine(x,y-symbol*1.25f,x,y-symbol*.1f,p);
        }
    }
}
