import pt.lembra.core.*; import java.util.*;
public class CatTest { public static void main(String[] a){
  List<Categories.Cat> l=Categories.defaults(); l.add(new Categories.Cat(7,"Ginásio",0xFF000000,7)); Categories.set(l);
  int f=0;
  if(Categories.match("ginasio")!=7) f++; if(Categories.match("gin")!=7) f++; if(Categories.match("trab")!=2) f++; if(Categories.match("Saúde")!=3) f++;
  QuickParser.Result r=QuickParser.parse("Treino amanhã às 7h #ginásio", System.currentTimeMillis());
  if(r.category!=7 || !r.title.equals("Treino")) f++;
  System.out.println(f==0?"ALL PASS":"FAIL "+f+" "+r.title+" "+r.category);
}}
