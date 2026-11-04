package com.seibel.distanthorizons.forge17.effects;

/** Fixed frame-local geometry budget. Stores values, never live effects or world objects. */
public final class EffectQuadBuffer
{
    public static final int CAPACITY=512, INTS_PER_QUAD=32;
    public final int[] vertices;
    public final int[] texture, sourceBlend, destinationBlend;
    public final int[] family, order;
    public final int[] traceBin, traceOrigin;
    public final int[] alphaSourceBlend, alphaDestinationBlend;
    public final boolean[] lit;
    public final float[] alphaCutoff, fade, ox, oy, oz;
    public final double[] distance;
    private final int[] priority, heap;
    public final int capacity;
    public int size;
    public EffectQuadBuffer() { this(CAPACITY); }
    /** The larger buffer only relocates existing native geometry; far admission still uses CAPACITY. */
    public EffectQuadBuffer(int capacity)
    {
        if(capacity<1 || capacity>HbmDrawRules.NEAR_CAPACITY) throw new IllegalArgumentException("geometry capacity");
        this.capacity=capacity;
        vertices=new int[capacity*INTS_PER_QUAD];
        texture=new int[capacity];sourceBlend=new int[capacity];destinationBlend=new int[capacity];
        family=new int[capacity];order=new int[capacity];traceBin=new int[capacity];traceOrigin=new int[capacity];
        alphaSourceBlend=new int[capacity];alphaDestinationBlend=new int[capacity];lit=new boolean[capacity];
        alphaCutoff=new float[capacity];fade=new float[capacity];ox=new float[capacity];oy=new float[capacity];oz=new float[capacity];
        distance=new double[capacity];priority=new int[capacity];heap=new int[capacity];
    }
    public void clear() { size=0; }
    public boolean add(int[] raw,int offset,int limit) { return admit(raw,offset,limit,0)>=0; }
    /** Stable reservoir: overload thins the whole trail instead of keeping only its oldest end. */
    public int admit(int[] raw,int offset,int limit,int rank)
    {
        limit=Math.max(0,Math.min(capacity,limit));
        if(limit==0 || offset<0 || offset>raw.length-INTS_PER_QUAD) return -1;
        int index;
        if(size<limit)
        {
            index=size;order[size]=size;priority[index]=rank;
            int child=size++;
            while(child>0)
            {
                int parent=(child-1)/2;
                if(priority[heap[parent]]>=rank) break;
                heap[child]=heap[parent];child=parent;
            }
            heap[child]=index;
        }
        else
        {
            index=heap[0];
            if(rank>=priority[index]) return -1;
            priority[index]=rank;
            int parent=0;
            while(parent*2+1<size)
            {
                int child=parent*2+1;
                if(child+1<size && priority[heap[child+1]]>priority[heap[child]]) child++;
                if(priority[heap[child]]<=rank) break;
                heap[parent]=heap[child];parent=child;
            }
            heap[parent]=index;
        }
        System.arraycopy(raw,offset,vertices,index*INTS_PER_QUAD,INTS_PER_QUAD);
        return index;
    }
    public void sortBackToFront()
    {
        for (int i=1;i<size;i++)
        {
            int q=order[i],j=i-1;
            while (j>=0 && distance[order[j]]<distance[q]) { order[j+1]=order[j];j--; }
            order[j+1]=q;
        }
    }
    public boolean sameState(int a,int b)
    {
        return texture[a]==texture[b] && sourceBlend[a]==sourceBlend[b] && destinationBlend[a]==destinationBlend[b]
            && alphaSourceBlend[a]==alphaSourceBlend[b] && alphaDestinationBlend[a]==alphaDestinationBlend[b]
            && lit[a]==lit[b] && alphaCutoff[a]==alphaCutoff[b] && ox[a]==ox[b] && oy[a]==oy[b] && oz[a]==oz[b];
    }
}
