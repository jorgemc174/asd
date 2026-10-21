package com.jorge.screenpen;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  int d(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    if(Settings.canDrawOverlays(this)&&!getIntent().getBooleanExtra("open_settings",false)){
       try{startForegroundService(new Intent(this,DrawService.class));finish();return;}catch(Exception ignored){}
    }
    LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(d(24),d(45),d(24),d(24));l.setBackgroundColor(0xff101523);
    TextView h=new TextView(this);h.setText("✎ S Pen Overlay");h.setTextColor(-1);h.setTextSize(29);l.addView(h);
    TextView t=new TextView(this);t.setText("Dibuja sobre lo que ves en pantalla, sin hacer capturas.\n\nConcede el permiso y activa el botón flotante.");t.setTextSize(16);t.setTextColor(0xffc3d3e9);t.setPadding(0,d(20),0,d(25));l.addView(t);
    Button grant=new Button(this);grant.setText("1. Permitir sobre otras apps");l.addView(grant);
    grant.setOnClickListener(v->startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),501));
    Button start=new Button(this);start.setText("2. Activar lápiz flotante");l.addView(start);
    start.setOnClickListener(v->{if(!Settings.canDrawOverlays(this)){grant.performClick();return;}
      if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},123);
      startForegroundService(new Intent(this,DrawService.class));finish();});
    Button stop=new Button(this);stop.setText("Detener");l.addView(stop);stop.setOnClickListener(v->stopService(new Intent(this,DrawService.class)));
    TextView info=new TextView(this);info.setText("\nToca ✎ para escribir.\n\n🔴🔵🟡 colores · ⌫ borrador\n↶ deshacer · ⊗ limpiar\nⅡ pausar para usar otras apps · × cerrar");info.setTextColor(0xffb6c3d5);info.setTextSize(16);l.addView(info);
    setContentView(l);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
     super.onActivityResult(request,result,data);
     if(request==501&&Settings.canDrawOverlays(this)){
       try{startForegroundService(new Intent(this,DrawService.class));finish();}catch(Exception ignored){}
     }
  }
  public static class DrawService extends Service {
    WindowManager wm; View ball, bar; Pad pad; boolean active, ballOn, barOn, navigating;
    WindowManager.LayoutParams bp,inkParams;
    ArrayList<TextView> colorButtons=new ArrayList<>();EraserIcon eraserButton;
    int dp(float f){return (int)(f*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(){super.onCreate();
      NotificationManager nm=getSystemService(NotificationManager.class);
      nm.createNotificationChannel(new NotificationChannel("drawing","Lápiz flotante",NotificationManager.IMPORTANCE_LOW));
      PendingIntent launch=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class).putExtra("open_settings",true),PendingIntent.FLAG_IMMUTABLE);
      startForeground(1888,new Notification.Builder(this,"drawing").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("S Pen Overlay activo").setContentText("Toca ✎ para dibujar").setContentIntent(launch).setOngoing(true).build());
      wm=(WindowManager)getSystemService(WINDOW_SERVICE);pad=new Pad(this);bubble();
    }
    @Override public int onStartCommand(Intent i,int f,int id){if(i!=null&&"stop".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}return START_STICKY;}
    WindowManager.LayoutParams lp(int w,int h){WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.START;return p;}
    TextView item(String label,int sz){TextView t=new TextView(this);t.setText(label);t.setTextSize(sz);t.setTextColor(-1);t.setGravity(Gravity.CENTER);return t;}
    void bubble(){
      if(ballOn)return;
      if(ball==null){
        TextView b=item("✎",32);GradientDrawable bg=new GradientDrawable();bg.setColor(0xff128fd2);bg.setShape(1);b.setBackground(bg);ball=b;
        final float[] down=new float[2];final int[] at=new int[2];final boolean[] drag={false};
        ball.setOnTouchListener((v,e)->{switch(e.getActionMasked()){
         case MotionEvent.ACTION_DOWN:down[0]=e.getRawX();down[1]=e.getRawY();at[0]=bp.x;at[1]=bp.y;drag[0]=false;return true;
         case MotionEvent.ACTION_MOVE:int dx=(int)(e.getRawX()-down[0]),dy=(int)(e.getRawY()-down[1]);if(Math.abs(dx)+Math.abs(dy)>dp(9))drag[0]=true;if(drag[0]){bp.x=at[0]+dx;bp.y=at[1]+dy;wm.updateViewLayout(ball,bp);}return true;
         case MotionEvent.ACTION_UP:if(!drag[0])showPad();return true;
        }return true;});
      }
      if(bp==null){bp=lp(dp(55),dp(55));bp.x=getResources().getDisplayMetrics().widthPixels-dp(67);bp.y=dp(270);}
      wm.addView(ball,bp);ballOn=true;
    }
    void hideBall(){if(ballOn){wm.removeView(ball);ballOn=false;}}
    void showPad(){
      if(active){
        if(navigating){
          hideBall();inkParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
          inkParams.alpha=1.0f;wm.updateViewLayout(pad,inkParams);
          navigating=false;toolbar();
        }
        return;
      }
      hideBall();inkParams=lp(-1,-1);wm.addView(pad,inkParams);active=true;navigating=false;toolbar();
    }
    void navigate(){if(!active)return;hideBar();
      inkParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      inkParams.alpha=.72f;wm.updateViewLayout(pad,inkParams);navigating=true;bubble();
    }
    void refreshTool(){
      for(int i=0;i<colorButtons.size();i++){
        TextView v=colorButtons.get(i),col=v;
        boolean selected=!pad.erase&&pad.color==new int[]{Color.RED,0xff168bff,0xffffd600,Color.WHITE}[i];
        v.setBackground(selected?bg(0xff34598e):null);
        v.setText(selected?"◉":"●");v.setTextColor(new int[]{Color.RED,0xff168bff,0xffffd600,Color.WHITE}[i]);
      }
      if(eraserButton!=null){eraserButton.selected=pad.erase;eraserButton.invalidate();}
    }
    GradientDrawable bg(int color){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(12));return gd;}
    void tool(LinearLayout l,String s,Runnable cb){TextView t=item(s,22);l.addView(t,new LinearLayout.LayoutParams(dp(38),dp(42)));t.setOnClickListener(v->{cb.run();refreshTool();});}
    void swatch(LinearLayout l,int color){TextView t=item("●",30);t.setTextColor(color);colorButtons.add(t);l.addView(t,new LinearLayout.LayoutParams(dp(37),dp(42)));
      t.setOnClickListener(v->{pad.choose(color);refreshTool();});}
    void toolbar(){
      if(bar==null){
        LinearLayout row=new LinearLayout(this);row.setPadding(dp(4),dp(3),dp(4),dp(3));row.setGravity(Gravity.CENTER);
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xf0151d30);shape.setCornerRadius(dp(15));row.setBackground(shape);
        swatch(row,Color.RED);swatch(row,0xff168bff);swatch(row,0xffffd600);swatch(row,Color.WHITE);
        eraserButton=new EraserIcon(this);eraserButton.setContentDescription("Goma");row.addView(eraserButton,new LinearLayout.LayoutParams(dp(38),dp(42)));
        eraserButton.setOnClickListener(v->{pad.erase=true;refreshTool();});
        tool(row,"↶",()->pad.undo());tool(row,"⊗",()->pad.clean());
        tool(row,"☞",this::navigate);tool(row,"×",this::stopSelf);bar=row;
      }
      WindowManager.LayoutParams p=lp(-2,dp(48));p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.y=dp(20);
      wm.addView(bar,p);barOn=true;refreshTool();
    }
    class EraserIcon extends View {
      Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);boolean selected;
      EraserIcon(Context c){super(c);}
      @Override protected void onDraw(Canvas c){
         float d=getResources().getDisplayMetrics().density;
         float x=getWidth()/2f,y=getHeight()/2f;
         if(selected){brush.setStyle(Paint.Style.FILL);brush.setColor(0xff34598e);
           c.drawRoundRect(0,0,getWidth(),getHeight(),dp(12),dp(12),brush);}
         brush.setColor(selected?0xffa4e6ff:0xffe5edff);brush.setStrokeWidth(2*d);
         brush.setStyle(Paint.Style.STROKE);brush.setStrokeJoin(Paint.Join.ROUND);brush.setStrokeCap(Paint.Cap.ROUND);
         Path e=new Path();e.moveTo(x-11*d,y+4*d);e.lineTo(x-1*d,y-10*d);
         e.quadTo(x+1*d,y-12*d,x+3*d,y-10*d);e.lineTo(x+12*d,y-2*d);
         e.quadTo(x+14*d,y,x+11*d,y+3*d);e.lineTo(x+2*d,y+12*d);
         e.lineTo(x-5*d,y+12*d);e.close();c.drawPath(e,brush);
         c.drawLine(x-3*d,y-6*d,x+5*d,y+3*d,brush);
         c.drawLine(x-10*d,y+14*d,x+12*d,y+14*d,brush);
      }
    }
    void hideBar(){if(barOn){wm.removeView(bar);barOn=false;}}
    @Override public void onDestroy(){hideBar();if(active)wm.removeView(pad);hideBall();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
    class Pad extends View {
      class Stroke {Path path=new Path();int color;boolean erase;Stroke(int c,boolean e){color=c;erase=e;}}
      ArrayList<Stroke> paths=new ArrayList<>();Stroke cur;int color=Color.RED;boolean erase=false;
      Paint p=new Paint(3);
      Pad(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
      boolean held(MotionEvent e){return (e.getButtonState()&(MotionEvent.BUTTON_STYLUS_PRIMARY|MotionEvent.BUTTON_STYLUS_SECONDARY))!=0;}
      void choose(int c){color=c;erase=false;}
      void undo(){if(!paths.isEmpty())paths.remove(paths.size()-1);invalidate();}
      void clean(){paths.clear();invalidate();}
      @Override protected void onDraw(Canvas c){int layer=c.saveLayer(0,0,getWidth(),getHeight(),null);for(Stroke s:paths){
        p.reset();p.setAntiAlias(true);p.setColor(s.color);p.setStrokeWidth(dp(s.erase?28:4));p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setXfermode(s.erase?new PorterDuffXfermode(PorterDuff.Mode.CLEAR):null);c.drawPath(s.path,p);
      }p.setXfermode(null);c.restoreToCount(layer);}
      @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){
        int tt=e.getToolType(0);cur=null;if(tt!=MotionEvent.TOOL_TYPE_STYLUS&&tt!=MotionEvent.TOOL_TYPE_ERASER)return true;
        cur=new Stroke(color,erase||tt==MotionEvent.TOOL_TYPE_ERASER||held(e));cur.path.moveTo(e.getX(),e.getY());cur.path.lineTo(e.getX()+.02f,e.getY()+.02f);paths.add(cur);invalidate();return true;
      }
        if(action==MotionEvent.ACTION_MOVE&&cur!=null){
        boolean now=erase||held(e)||e.getToolType(0)==MotionEvent.TOOL_TYPE_ERASER;
        if(cur.erase!=now){cur=new Stroke(color,now);cur.path.moveTo(e.getX(),e.getY());paths.add(cur);}
        for(int k=0;k<e.getHistorySize();k++)cur.path.lineTo(e.getHistoricalX(k),e.getHistoricalY(k));cur.path.lineTo(e.getX(),e.getY());invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(cur!=null)cur.path.lineTo(e.getX(),e.getY());cur=null;invalidate();return true;}return true;}
    }
  }
}