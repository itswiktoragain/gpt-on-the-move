/** Mock GameRenderer: ray-to-block intersection on a plane 5 blocks ahead. */
public final class glq {
    public bum player=new bum();
    public fbb hit;
    public int hitBlockX,hitBlockY;
    public float a(fks camera,float tick,boolean world){return 70f;}
    public void a(float tick){
        hit=player.g(tick);
        hitBlockX=(int)Math.floor(hit.d/Math.max(.001,hit.f)*5);
        hitBlockY=(int)Math.floor(hit.e/Math.max(.001,hit.f)*5);
    }
}