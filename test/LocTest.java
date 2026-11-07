import pt.lembra.core.*; import java.util.*;
public class LocTest { public static void main(String[] a){
  int f=0; long now=System.currentTimeMillis();
  Reminder r=new Reminder(); r.id=1; r.locPlace=5; r.dueAt=now-100000;
  if(!Occurrences.between(r,now-86400000L,now+86400000L,10).isEmpty()) f++;
  r.snoozedUntil=now+3600000; if(Occurrences.between(r,now,now+86400000L,10).size()!=1) f++;
  Reminder o=new Reminder(); o.id=2; o.dueAt=r.snoozedUntil;
  if(Occurrences.nextConflict(r,Arrays.asList(r,o),0)==null) f++;
  r.snoozedUntil=0; if(Occurrences.nextConflict(r,Arrays.asList(r,o),0)!=null) f++;
  Reminder c=r.copy(); if(c.locPlace!=5||c.locTrigger!=Reminder.LOC_ARRIVE) f++;
  System.out.println(f==0?"ALL PASS":"FAIL "+f);
}}
