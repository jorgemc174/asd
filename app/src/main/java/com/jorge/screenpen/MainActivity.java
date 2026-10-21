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
    TextView info=new TextView(this);info.setText("\nToca ✎ para escribir.\n\n⠿ arrastrar · ↻ girar · ⌄ plegar\n◉ colores · goma · ↶ deshacer\n☞ navegar · × cerrar");info.setTextColor(0xffb6c3d5);info.setTextSize(16);l.addView(info);
    setContentView(l);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
     super.onActivityResult(request,result,data);
     if(request==501&&Settings.canDrawOverlays(this)){
       try{startForegroundService(new Intent(this,DrawService.class));finish();}catch(Exception ignored){}
     }
  }
  public static class DrawService extends Service {
    WindowManager wm; View ball, bar; Pad pad; boolean active, ballOn, barOn, navigating, verticalMenu;
    WindowManager.LayoutParams bp,inkParams,menuParams;
    ArrayList<TextView> colorButtons=new ArrayList<>();EraserIcon eraserButton;
    int dp(float f){return (int)(f*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(){super.onCreate();
      NotificationManager nm=getSystemService(NotificationManager.class);
      nm.createNotificationChannel(new NotificationChannel("drawing","Lápiz flotante",NotificationManager.IMPORTANCE_LOW));
      PendingIntent launch=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class).putExtra("open_settings",true),PendingIntent.FLAG_IMMUTABLE);
      startForeground(1888,new Notification.Builder(this,"drawing").setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("S Pen Overlay activo").setContentText("Toca ✎ para dibujar").setContentIntent(launch).setOngoing(true).build());
      wm=(WindowManager)getSystemService(WINDOW_SERVICE);pad=new Pad(this);verticalMenu=getSharedPreferences("floating_menu",MODE_PRIVATE).getBoolean("vertical",false);bubble();
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
         case MotionEvent.ACTION_MOVE:int dx=(int)(e.getRawX()-down[0]),dy=(int)(e.getRawY()-down[1]);if(Math.abs(dx)+Math.abs(dy)>dp(9))drag[0]=true;if(drag[0]){bp.x=clamp(at[0]+dx,dp(8),screenWidth()-dp(62));bp.y=clamp(at[1]+dy,dp(8),screenHeight()-dp(62));wm.updateViewLayout(ball,bp);if(barOn)positionMenu();}return true;
         case MotionEvent.ACTION_UP:if(!drag[0])showPad();return true;
        }return true;});
      }
      if(bp==null){bp=lp(dp(55),dp(55));bp.x=getResources().getDisplayMetrics().widthPixels-dp(67);bp.y=dp(270);}
      wm.addView(ball,bp);ballOn=true;
    }
    int screenWidth(){return getResources().getDisplayMetrics().widthPixels;}
    int screenHeight(){return getResources().getDisplayMetrics().heightPixels;}
    int clamp(int value,int low,int high){return Math.max(low,Math.min(value,Math.max(low,high)));}
    void hideBall(){if(ballOn){ball.animate().cancel();wm.removeView(ball);ballOn=false;}}

    void showPad(){
      if(!active){
        hideBall();
        inkParams=lp(-1,-1);wm.addView(pad,inkParams);
        active=true;navigating=false;bubble();
        toolbar();return;
      }
      if(navigating){
        inkParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        inkParams.alpha=1f;wm.updateViewLayout(pad,inkParams);
        navigating=false;toolbar();return;
      }
      if(barOn)hideBar(true);else toolbar();
    }
    void navigate(){if(!active)return;
      hideBar(false);
      inkParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
      inkParams.alpha=.72f;wm.updateViewLayout(pad,inkParams);navigating=true;
    }
    void refreshTool(){
      for(int i=0;i<colorButtons.size();i++){
        TextView v=colorButtons.get(i);
        int[] colors={Color.RED,0xff168bff,0xffffd600,Color.WHITE};
        boolean selected=!pad.erase&&pad.color==colors[i];
        v.setBackground(selected?bg(0xff34598e):null);
        v.setText(selected?"◉":"●");v.setTextColor(colors[i]);
      }
      if(eraserButton!=null){eraserButton.selected=pad.erase;eraserButton.invalidate();}
    }
    GradientDrawable bg(int color){GradientDrawable gd=new GradientDrawable();gd.setColor(color);gd.setCornerRadius(dp(14));return gd;}
    TextView button(String text,int size){TextView t=item(text,size);t.setTextColor(0xffe9f1ff);return t;}
    void tool(LinearLayout l,String icon,Runnable cb){TextView t=button(icon,22);l.addView(t,new LinearLayout.LayoutParams(dp(40),dp(42)));t.setOnClickListener(v->{cb.run();refreshTool();});}
    void swatch(LinearLayout l,int color){TextView t=item("●",30);t.setTextColor(color);colorButtons.add(t);l.addView(t,new LinearLayout.LayoutParams(dp(40),dp(42)));
      t.setOnClickListener(v->{pad.choose(color);refreshTool();});}
    int menuWidth(){return dp(verticalMenu?128:352);}
    int menuHeight(){return dp(verticalMenu?284:99);}
    void positionMenu(){
       if(menuParams==null||bar==null)return;
       int width=menuParams.width,height=menuParams.height;
       int x,y;
       if(verticalMenu){
          x=bp.x>screenWidth()/2?bp.x-width-dp(8):bp.x+dp(62);
          y=bp.y-dp(25);
       }else{
          x=bp.x+dp(27)-width/2;
          y=bp.y+dp(62);
          if(y+height>screenHeight()-dp(8))y=bp.y-height-dp(8);
       }
       menuParams.x=clamp(x,dp(8),screenWidth()-width-dp(8));
       menuParams.y=clamp(y,dp(8),screenHeight()-height-dp(8));
       if(bar.getParent()!=null)wm.updateViewLayout(bar,menuParams);
    }
    void moveTogether(View handle){
      final float[] first=new float[2];final int[] origin=new int[2];final boolean[] drag={false};
      handle.setOnTouchListener((v,e)->{
       switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:
          first[0]=e.getRawX();first[1]=e.getRawY();origin[0]=bp.x;origin[1]=bp.y;drag[0]=false;return true;
        case MotionEvent.ACTION_MOVE:
          int dx=(int)(e.getRawX()-first[0]),dy=(int)(e.getRawY()-first[1]);
          if(Math.abs(dx)+Math.abs(dy)>dp(6))drag[0]=true;
          if(drag[0]){bp.x=clamp(origin[0]+dx,dp(8),screenWidth()-dp(62));
            bp.y=clamp(origin[1]+dy,dp(8),screenHeight()-dp(62));
            wm.updateViewLayout(ball,bp);if(barOn)positionMenu();}
          return true;
        case MotionEvent.ACTION_UP:return true;
       }return true;
      });
    }
    void pair(LinearLayout parent,boolean colors){
      LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);
      if(colors){swatch(r,Color.RED);swatch(r,0xff168bff);}
      else{tool(r,"↶",()->pad.undo());tool(r,"☞",this::navigate);}
      parent.addView(r,new LinearLayout.LayoutParams(-1,dp(43)));
    }
    void toolbar(){
      if(navigating)return;
      if(barOn)return;
      if(bar==null){
        colorButtons.clear();eraserButton=null;
        LinearLayout dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(8),dp(5),dp(8),dp(5));
        GradientDrawable background=bg(0xf51a243b);
        background.setStroke(dp(1),0xff546987);
        dock.setBackground(background);dock.setElevation(dp(14));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView drag=button("⠿",25);drag.setContentDescription("Mover barra y lápiz flotante");
        header.addView(drag,new LinearLayout.LayoutParams(dp(verticalMenu?28:38),dp(35)));moveTogether(drag);
        TextView brand=button("S PEN",11);brand.setTextColor(0xff73e5d0);brand.setTypeface(null,1);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(35),1));
        TextView rotate=button("↻",24);rotate.setContentDescription("Girar barra horizontal y vertical");
        header.addView(rotate,new LinearLayout.LayoutParams(dp(32),dp(35)));rotate.setOnClickListener(v->rotateToolbar());
        TextView collapse=button("⌄",24);collapse.setContentDescription("Plegar menú");
        header.addView(collapse,new LinearLayout.LayoutParams(dp(32),dp(35)));collapse.setOnClickListener(v->hideBar(true));
        TextView exit=button("×",24);exit.setContentDescription("Cerrar aplicación");
        if(!verticalMenu){header.addView(exit,new LinearLayout.LayoutParams(dp(30),dp(35)));exit.setOnClickListener(v->stopSelf());}
        dock.addView(header,new LinearLayout.LayoutParams(-1,dp(36)));
        if(verticalMenu){
          LinearLayout r1=new LinearLayout(this);r1.setGravity(Gravity.CENTER);
          swatch(r1,Color.RED);swatch(r1,0xff168bff);
          dock.addView(r1,new LinearLayout.LayoutParams(-1,dp(43)));
          LinearLayout r2=new LinearLayout(this);r2.setGravity(Gravity.CENTER);
          swatch(r2,0xffffd600);swatch(r2,Color.WHITE);
          dock.addView(r2,new LinearLayout.LayoutParams(-1,dp(43)));
          LinearLayout r3=new LinearLayout(this);r3.setGravity(Gravity.CENTER);
          eraserButton=new EraserIcon(this);eraserButton.setContentDescription("Goma");
          r3.addView(eraserButton,new LinearLayout.LayoutParams(dp(40),dp(42)));
          eraserButton.setOnClickListener(v->{pad.erase=true;refreshTool();});
          tool(r3,"↶",()->pad.undo());
          dock.addView(r3,new LinearLayout.LayoutParams(-1,dp(43)));
          LinearLayout r4=new LinearLayout(this);r4.setGravity(Gravity.CENTER);
          tool(r4,"⊗",()->pad.clean());tool(r4,"☞",this::navigate);
          dock.addView(r4,new LinearLayout.LayoutParams(-1,dp(43)));
          TextView stop=button("SALIR ×",12);stop.setContentDescription("Cerrar aplicación");
          dock.addView(stop,new LinearLayout.LayoutParams(-1,dp(37)));stop.setOnClickListener(v->stopSelf());
        }else{
          LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
          swatch(row,Color.RED);swatch(row,0xff168bff);swatch(row,0xffffd600);swatch(row,Color.WHITE);
          eraserButton=new EraserIcon(this);eraserButton.setContentDescription("Goma");
          row.addView(eraserButton,new LinearLayout.LayoutParams(dp(40),dp(42)));
          eraserButton.setOnClickListener(v->{pad.erase=true;refreshTool();});
          tool(row,"↶",()->pad.undo());tool(row,"⊗",()->pad.clean());tool(row,"☞",this::navigate);
          dock.addView(row,new LinearLayout.LayoutParams(-1,dp(43)));
        }
        bar=dock;
      }
      menuParams=lp(Math.min(menuWidth(),screenWidth()-dp(16)),menuHeight());
      positionMenu();
      bar.animate().cancel();
      if(bar.getParent()==null)wm.addView(bar,menuParams);
      else wm.updateViewLayout(bar,menuParams);
      barOn=true;
      if(ball instanceof TextView)((TextView)ball).setText("⌄");
      bar.setPivotX(clamp(bp.x+dp(27)-menuParams.x,0,menuParams.width));
      bar.setPivotY(clamp(bp.y+dp(27)-menuParams.y,0,menuParams.height));
      bar.setScaleX(.76f);bar.setScaleY(.76f);bar.setAlpha(0f);bar.setTranslationY(dp(12));
      bar.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(240)
        .setInterpolator(new android.view.animation.OvershootInterpolator(.8f)).start();
      refreshTool();
    }
    void hideBar(boolean animate){
       if(bar==null)return;
       bar.animate().cancel();barOn=false;
       if(ball instanceof TextView)((TextView)ball).setText("✎");
       final View closing=bar;
       if(animate&&bar.getParent()!=null)
         bar.animate().alpha(0).scaleX(.78f).scaleY(.78f).translationY(dp(8)).setDuration(130)
           .withEndAction(()->{if(!barOn&&closing.getParent()!=null)wm.removeView(closing);}).start();
       else if(bar.getParent()!=null)wm.removeView(bar);
    }
    void rotateToolbar(){
      if(!barOn)return;
      final View old=bar;barOn=false;
      old.animate().cancel();
      old.animate().alpha(.05f).scaleX(.86f).scaleY(.86f).rotation(8f).setDuration(120)
        .withEndAction(()->{
           if(old.getParent()!=null)wm.removeView(old);
           verticalMenu=!verticalMenu;
           getSharedPreferences("floating_menu",MODE_PRIVATE).edit().putBoolean("vertical",verticalMenu).apply();
           bar=null;toolbar();
        }).start();
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
    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig){
      super.onConfigurationChanged(newConfig);
      if(bp!=null){bp.x=clamp(bp.x,dp(8),screenWidth()-dp(62));
        bp.y=clamp(bp.y,dp(8),screenHeight()-dp(62));
        if(ballOn)wm.updateViewLayout(ball,bp);}
      if(barOn)positionMenu();
    }
    @Override public void onDestroy(){
      if(bar!=null){bar.animate().cancel();if(bar.getParent()!=null)wm.removeView(bar);}
      if(active)wm.removeView(pad);hideBall();super.onDestroy();
    }
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