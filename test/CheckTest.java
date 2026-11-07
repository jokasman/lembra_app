import pt.lembra.core.*; import java.util.*;
public class CheckTest { public static void main(String[] a){
  String t="Toalha\n- Touca\n• Óculos\n\n1. Chinelos\n2) Gel de banho\n[x] Fato de banho\n[ ] Cadeado\n☑ Cartão\n1.5 kg arroz\n  * Garrafa de água  ";
  List<ChecklistText.Item> l=ChecklistText.parse(t); int f=0;
  String[] exp={"Toalha","Touca","Óculos","Chinelos","Gel de banho","Fato de banho","Cadeado","Cartão","1.5 kg arroz","Garrafa de água"};
  boolean[] dn={false,false,false,false,false,true,false,true,false,false};
  if(l.size()!=exp.length) f++; else for(int i=0;i<exp.length;i++){ if(!l.get(i).text.equals(exp[i])||l.get(i).done!=dn[i]){f++; System.out.println("bad "+i+" "+l.get(i).text+" "+l.get(i).done);} }
  System.out.println(f==0?"ALL PASS":"FAIL "+f+" size="+l.size());
}}
