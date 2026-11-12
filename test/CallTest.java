import pt.lembra.core.*;
public class CallTest { public static void main(String[] a){
  int f=0;
  // Chamada feita ou atendida (esteve em linha), com ou sem registo disponível.
  if(CallFollowUp.classify(false,true,CallFollowUp.LOG_OUTGOING)!=CallFollowUp.ANSWERED) f++;
  if(CallFollowUp.classify(true,true,CallFollowUp.LOG_INCOMING)!=CallFollowUp.ANSWERED) f++;
  if(CallFollowUp.classify(false,true,CallFollowUp.LOG_UNKNOWN)!=CallFollowUp.ANSWERED) f++;
  // Tocou sem atender: rejeitada, salvo se o registo disser que foi perdida.
  if(CallFollowUp.classify(true,false,CallFollowUp.LOG_REJECTED)!=CallFollowUp.REJECTED) f++;
  if(CallFollowUp.classify(true,false,CallFollowUp.LOG_UNKNOWN)!=CallFollowUp.REJECTED) f++;
  if(CallFollowUp.classify(true,false,CallFollowUp.LOG_MISSED)!=CallFollowUp.MISSED) f++;
  // Bloqueadas, correio de voz e atendidas noutro aparelho não perguntam.
  if(CallFollowUp.classify(true,false,CallFollowUp.LOG_BLOCKED)!=CallFollowUp.NONE) f++;
  if(CallFollowUp.classify(true,true,CallFollowUp.LOG_ANSWERED_EXTERNALLY)!=CallFollowUp.NONE) f++;
  if(CallFollowUp.classify(false,false,CallFollowUp.LOG_UNKNOWN)!=CallFollowUp.NONE) f++;
  // Máscara das definições: por defeito atendidas e rejeitadas, não perdidas.
  int m=CallFollowUp.DEFAULT_MASK;
  if(!CallFollowUp.shouldAsk(CallFollowUp.ANSWERED,m)||!CallFollowUp.shouldAsk(CallFollowUp.REJECTED,m)) f++;
  if(CallFollowUp.shouldAsk(CallFollowUp.MISSED,m)||CallFollowUp.shouldAsk(CallFollowUp.NONE,m)) f++;
  if(CallFollowUp.shouldAsk(CallFollowUp.ANSWERED,0)||!CallFollowUp.shouldAsk(CallFollowUp.MISSED,CallFollowUp.MISSED)) f++;
  // Título proposto.
  if(!CallFollowUp.title(" João Silva ","912345678","15:42").equals("Ligar a João Silva")) f++;
  if(!CallFollowUp.title("","912 345 678","15:42").equals("Ligar a 912 345 678")) f++;
  if(!CallFollowUp.title(null,null,"15:42").equals("Seguimento da chamada das 15:42")) f++;
  // Opções rápidas pedidas: 10 min, 30 min, 1 hora, 4 horas.
  int[] t=CallFollowUp.MINUTES;
  if(t.length!=4||t[0]!=10||t[1]!=30||t[2]!=60||t[3]!=240) f++;
  System.out.println(f==0?"ALL PASS":"FAIL "+f);
}}
