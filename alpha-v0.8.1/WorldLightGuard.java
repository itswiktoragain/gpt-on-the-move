package alphamodern;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
/** Keeps Alpha's legacy light scheduler beneath its recursive 100,000-job flush threshold. */
public final class WorldLightGuard {
 private static final AtomicInteger ACTIVE=new AtomicInteger();
 private static final AtomicLong DROPPED=new AtomicLong();
 private static final int LIMIT=Math.max(256,Math.min(32768,Integer.getInteger("alpha.modern.lightQueueLimit",8192)));
 private WorldLightGuard(){}
 public static void beginBridge(){
  if(ACTIVE.incrementAndGet()==1){
   DROPPED.set(0);
   System.out.println(AlphaModernAgent.PREFIX+"Safe lighting queue enabled (max "+LIMIT+" pending jobs)");
  }
 }
 public static void endBridge(){
  while(true){int n=ACTIVE.get();if(n==0)return;if(ACTIVE.compareAndSet(n,n-1))return;}
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 public static boolean offer(List jobs,Object job){
  if(ACTIVE.get()==0 || jobs.size()<LIMIT)return jobs.add(job);
  long n=DROPPED.incrementAndGet();
  if(n==1 || (n%50000)==0)
   System.out.println(AlphaModernAgent.PREFIX+"Deferred "+n+" legacy relighting jobs");
  return false;
 }
 public static long dropped(){return DROPPED.get();}
}
