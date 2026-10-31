package app.aura.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Consistent 24-unit outline icons, independent of the device's emoji font. */
final class AuraIcon extends Drawable {
    private final String kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    AuraIcon(String kind,int color){this.kind=kind;p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
        switch(kind){
            case "ping":canvas.drawArc(new RectF(3,3,21,21),145,250,false,p);canvas.drawLine(12,12,17,7,p);canvas.drawCircle(12,12,1.1f,p);canvas.drawLine(5,19,19,19,p);break;
            case "settings":for(int i=0;i<3;i++){float y=5+i*7,x=i==1?8:16;canvas.drawLine(3,y,x-3,y,p);canvas.drawLine(x+3,y,21,y,p);canvas.drawCircle(x,y,2.7f,p);}break;
            case "back":canvas.drawLine(14,5,7,12,p);canvas.drawLine(7,12,14,19,p);break;
            case "chevron":canvas.drawLine(9,7,14,12,p);canvas.drawLine(14,12,9,17,p);break;
            case "check":canvas.drawLine(5,12,10,17,p);canvas.drawLine(10,17,20,6,p);break;
            case "add":canvas.drawLine(12,4,12,20,p);canvas.drawLine(4,12,20,12,p);break;
            case "server":canvas.drawRoundRect(new RectF(3,3,21,10),2,2,p);canvas.drawRoundRect(new RectF(3,14,21,21),2,2,p);canvas.drawPoint(7,6.5f,p);canvas.drawPoint(7,17.5f,p);break;
            case "shield":Path shield=new Path();shield.moveTo(12,2);shield.lineTo(21,6);shield.lineTo(20,14);shield.quadTo(18,19,12,22);shield.quadTo(6,19,4,14);shield.lineTo(3,6);shield.close();canvas.drawPath(shield,p);break;
            case "copy":canvas.drawRoundRect(new RectF(8,8,21,21),2,2,p);canvas.drawLine(16,3,3,3,p);canvas.drawLine(3,3,3,16,p);break;
            case "qr":for(int x:new int[]{3,14})for(int y:new int[]{3,14})canvas.drawRoundRect(new RectF(x,y,x+7,y+7),1,1,p);break;
            case "palette":canvas.drawCircle(12,12,9,p);canvas.drawCircle(8,8,1,p);canvas.drawCircle(15,7,1,p);canvas.drawCircle(7,14,1,p);canvas.drawCircle(16,15,2,p);break;
            case "route":canvas.drawLine(7,3,7,21,p);canvas.drawLine(7,14,18,5,p);canvas.drawLine(13,5,18,5,p);canvas.drawLine(18,5,18,10,p);break;
            default:canvas.drawRoundRect(new RectF(5,3,19,21),2,2,p);canvas.drawLine(9,8,15,8,p);canvas.drawLine(9,12,15,12,p);canvas.drawLine(9,16,13,16,p);
        }canvas.restore();}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
