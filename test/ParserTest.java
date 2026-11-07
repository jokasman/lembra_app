import pt.lembra.core.*;
import java.time.*;
public class ParserTest {
  static int fail=0;
  static long now = LocalDateTime.of(2026,10,31,10,0).atZone(ZoneId.of("Europe/Lisbon")).toInstant().toEpochMilli();
  static String fmt(Long ms){ return ms==null?"null":LocalDateTime.ofInstant(Instant.ofEpochMilli(ms),ZoneId.of("Europe/Lisbon")).toString(); }
  static void t(String in, String title, String due, int rep){
    QuickParser.Result r = QuickParser.parse(in, now);
    String got = fmt(r.dueAt);
    boolean ok = r.title.equals(title) && got.startsWith(due) && r.repeatType==rep;
    if(!ok) fail++;
    System.out.println((ok?"OK  ":"FAIL")+" | "+in+" -> ["+r.title+"] "+got+" rep="+r.repeatType+" mask="+r.weekMask+" md="+r.monthDay+" int="+r.interval+"/"+r.intervalUnit+" | "+r.summary(now));
  }
  public static void main(String[] a){
    Recurrence.zone = ZoneId.of("Europe/Lisbon");
    t("Ligar ao Tiago amanhã às 15h30","Ligar ao Tiago","2026-11-01T15:30",0);
    t("Pagar renda dia 8 todos os meses às 10h","Pagar renda","2026-11-08T10:00",4);
    t("Beber água a cada 2 horas","Beber água","2026-10-31T12:00",6);
    t("Reunião na próxima sexta às 9","Reunião","2026-11-06T09:00",0);
    t("Tirar pão do forno daqui a 20 min","Tirar pão do forno","2026-10-31T10:20",0);
    t("Ginásio todas as segundas e quartas às 19h","Ginásio","2026-11-02T19:00",3);
    t("Tomar vitamina todos os dias às 8h","Tomar vitamina","2026-11-01T08:00",1);
    t("Enviar relatório dias úteis às 17h30 #trabalho !","Enviar relatório","2026-11-02T17:30",2);
    t("Aniversário da Ana 15 de março todos os anos","Aniversário da Ana","2027-03-15T09:00",5);
    t("Consulta 12/11 às 14:15","Consulta","2026-11-12T14:15",0);
    t("Comprar leite","Comprar leite","null",0);
    t("Jantar com amigos hoje à noite","Jantar com amigos","2026-10-31T20:00",0);
    t("Lembra-me de regar as plantas sábado de manhã","Regar as plantas","2026-11-07T09:00",0);
    t("Ver email às 9","Ver email","2026-11-01T09:00",0);
    t("Pagar IUC dia 5","Pagar IUC","2026-11-05T09:00",0);
    t("Cortar cabelo daqui a 3 semanas","Cortar cabelo","2026-11-21T10:00",0);
    t("Trocar óleo de 6 em 6 meses","Trocar óleo","2027-04-30T09:00",6);
    t("Reunião de equipa às 8 da noite","Reunião de equipa","2026-10-31T20:00",0);
    t("Lixo às terças às 21h","Lixo","2026-11-03T21:00",3);
    t("Almoço com a Rita amanhã ao meio-dia","Almoço com a Rita","2026-11-01T12:00",0);
    t("Telefonar à mãe domingo às 18h","Telefonar à mãe","2026-11-01T18:00",0);
    t("Rever orçamento em 2 horas","Rever orçamento","2026-10-31T12:00",0);
    t("Backup semanalmente sexta 16h","Backup","2026-11-06T16:00",3);
    // Recorrência
    Reminder r=new Reminder(); r.repeatType=Reminder.REPEAT_MONTHLY; r.monthDay=31;
    r.dueAt=LocalDateTime.of(2026,1,31,9,0).atZone(Recurrence.zone).toInstant().toEpochMilli();
    long n=Recurrence.rawNext(r,r.dueAt); System.out.println("mensal 31 jan -> "+fmt(n)); if(!fmt(n).startsWith("2026-02-28")) fail++;
    n=Recurrence.rawNext(r,n); System.out.println("  -> "+fmt(n)); if(!fmt(n).startsWith("2026-03-31")) fail++;
    Reminder w=new Reminder(); w.repeatType=Reminder.REPEAT_WEEKLY; w.weekMask=1|4; // seg, qua
    w.dueAt=LocalDateTime.of(2026,11,2,19,0).atZone(Recurrence.zone).toInstant().toEpochMilli();
    long x=w.dueAt; StringBuilder sb=new StringBuilder(); for(int i=0;i<4;i++){x=Recurrence.rawNext(w,x); sb.append(fmt(x).substring(0,10)).append(' ');} System.out.println("seg/qua: "+sb); if(!sb.toString().equals("2026-11-04 2026-11-09 2026-11-11 2026-11-16 ")) fail++;
    Reminder c=new Reminder(); c.repeatType=Reminder.REPEAT_DAILY; c.endType=Reminder.END_COUNT; c.endCount=3; c.dueAt=now;
    int k=0; while(Recurrence.advance(c, c.dueAt)) k++; System.out.println("count3 advances="+k); if(k!=2) fail++;
    Reminder ov=new Reminder(); ov.repeatType=Reminder.REPEAT_DAILY; ov.dueAt=now-5L*86400000; Recurrence.advance(ov, now); System.out.println("overdue daily -> "+fmt(ov.dueAt)+" count="+ov.count); if(!fmt(ov.dueAt).startsWith("2026-11-01T10:00")) fail++;
    System.out.println(fail==0?"ALL PASS":"FAILURES: "+fail);
  }
}
