import pt.lembra.core.*;
import java.time.*; import java.util.*;
public class OccTest {
  static long at(int mo,int d,int h,int m){ return LocalDateTime.of(2026,mo,d,h,m).atZone(Recurrence.zone).toInstant().toEpochMilli(); }
  public static void main(String[] x){
    Recurrence.zone=ZoneId.of("Europe/Lisbon"); int fail=0;
    long now=at(10,31,10,0);
    Reminder a=new Reminder(); a.id=1; a.title="Ginásio"; a.repeatType=Reminder.REPEAT_WEEKLY; a.weekMask=1|4; a.dueAt=at(11,2,19,0);
    Reminder b=new Reminder(); b.id=2; b.title="Reunião"; b.dueAt=at(11,4,19,0);
    Reminder c=new Reminder(); c.id=3; c.title="Jantar"; c.dueAt=at(11,4,19,10);
    List<Reminder> all=Arrays.asList(a,b,c);
    List<Long> nov=Occurrences.between(a,at(11,1,0,0),at(11,30,23,59),100); System.out.println("nov occ="+nov.size()); if(nov.size()!=9) fail++;
    List<Occurrences.Conflict> cs=Occurrences.conflicts(a,all,now,60L*86400000,0,10); System.out.println("a conflicts(0)="+cs.size()+" "+(cs.isEmpty()?"":cs.get(0).other.title)); if(cs.size()!=1) fail++;
    cs=Occurrences.conflicts(c,all,now,60L*86400000,15,10); System.out.println("c conflicts(15)="+cs.size()); if(cs.size()!=2) fail++;
    cs=Occurrences.conflicts(c,all,now,60L*86400000,0,10); System.out.println("c conflicts(0)="+cs.size()); if(cs.size()!=0) fail++;
    Occurrences.Conflict n=Occurrences.nextConflict(b,all,0); System.out.println("b next="+(n==null?null:n.other.title)); if(n==null||n.other.id!=1) fail++;
    System.out.println(fail==0?"ALL PASS":"FAIL "+fail);
  }
}
