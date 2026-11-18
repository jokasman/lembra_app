import pt.lembra.core.*;
public class PauseTest { public static void main(String[] a){
  int f=0;
  long now=1_700_000_123_456L, min=PauseWindow.floorMinute(now);
  if(min%60000!=0||min>now||now-min>=60000) f++;
  long[] w=PauseWindow.forHours(now,4);
  if(w[0]!=min||w[1]-w[0]!=4*3600000L) f++;
  if(PauseWindow.state(0,0,now)!=PauseWindow.NONE) f++;
  if(PauseWindow.state(w[0],w[1],now)!=PauseWindow.ACTIVE) f++;
  if(PauseWindow.state(now+60000,now+120000,now)!=PauseWindow.SCHEDULED) f++;
  if(PauseWindow.state(now-120000,now-60000,now)!=PauseWindow.NONE) f++;
  if(PauseWindow.validate(w[0],w[1],now)!=null) f++;
  if(PauseWindow.validate(w[1],w[0],now)==null) f++;
  if(PauseWindow.validate(now-120000,now-60000,now)==null) f++;
  if(PauseWindow.shiftEnd(0,7200000L,3600000L)!=3600000L+7200000L) f++;
  if(!PauseWindow.length(w[0],w[1]).equals("4 h")) f++;
  if(!PauseWindow.length(0,24*3600000L).equals("1 dia")) f++;
  int[] h=PauseWindow.HOURS; if(h.length!=6||h[0]!=1||h[5]!=24) f++;
  System.out.println(f==0?"ALL PASS":"FAIL "+f);
}}
