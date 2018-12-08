import java.util.PriorityQueue;
import java.util.Arrays;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor(int debug)
	{
		myDebugLevel = debug;
	}
	
	 public HuffProcessor()
	  {
	    this(0);
	  }

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream i, BitOutputStream o)
	{
	  int[] counting = readCounting(i);
	  if(myDebugLevel==DEBUG_HIGH)
	  {
	    System.out.println(Arrays.toString(counting));
	  }
	  HuffNode r = makeTreeCounting(counting);
	  if(myDebugLevel==DEBUG_HIGH)
	  {
	    System.out.println(r);
	  }
	  String[] codes = makeCodesTree(r);
	  if(myDebugLevel==DEBUG_HIGH)
	  {
	    System.out.println(Arrays.toString(codes));
	  }
	  o.writeBits(BITS_PER_INT, HUFF_TREE);
	  writeHead(r, o);
	  i.reset();
	  writeCompressed(codes, i, o);
	  o.close();
	}
	
	private int[] readCounting(BitInputStream i)
	{
	  int[] answer = new int[ALPH_SIZE+1];
	  int b = i.readBits(BITS_PER_WORD);
	  while(b!=-1)
	  {
	    answer[b]++;
	    b=i.readBits(BITS_PER_WORD);
	  }
	  answer[PSEUDO_EOF] = 1;
	  return(answer);
	}
	
	private HuffNode makeTreeCounting(int[] counting)
	{
	  PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
	  for(int i=0; i<counting.length; i++)
	  {
	    if(counting[i]!=0)
	    {
	      pq.add(new HuffNode(i, counting[i], null, null));
	    }
	  }
	  while(pq.size()>1)
	  {
	    HuffNode left = pq.remove();
	    HuffNode right = pq.remove();
	    HuffNode gang = new HuffNode(0, left.myWeight+right.myWeight, left, right);
	    pq.add(gang);
	  }
	  HuffNode answer = pq.remove();
	  return(answer);
	}
	
	private String[] makeCodesTree(HuffNode r)
	{
	  String[] codes = new String[ALPH_SIZE+1];
	  ayuda(r, codes, "");
	  return(codes);
	}
	
	private void ayuda(HuffNode r, String[] codes, String path)
	{
	  if(r.myLeft==null && r.myRight==null)
	  {
	    codes[r.myValue] = path;
	    return;
	  }
	  ayuda(r.myLeft, codes, path+0);
	  ayuda(r.myRight, codes, path+1);
	}
	
	private void writeHead(HuffNode r, BitOutputStream o)
	{ 
	  if(r.myLeft==null && r.myRight==null)
	  {
	    o.writeBits(1, 1);
	    o.writeBits(BITS_PER_WORD+1, r.myValue);
	    return;
	  }
	  else
	  {
	    o.writeBits(1, 0);
	    writeHead(r.myLeft, o);
	    writeHead(r.myRight, o);
	  }
	}
	
	private void writeCompressed(String[] codes, BitInputStream i, BitOutputStream o)
	{
	  int b = i.readBits(BITS_PER_WORD);
	  while(b>-1)
	  {
      String path = codes[b];
      o.writeBits(path.length(), Integer.parseInt(path, 2));
      b=i.readBits(BITS_PER_WORD);
	  }
	   String path = codes[PSEUDO_EOF];
	   o.writeBits(path.length(), Integer.parseInt(path, 2));
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream i, BitOutputStream o)
	{
	  int allBits = i.readBits(BITS_PER_INT);
	  if(allBits!=HUFF_TREE)
	    throw new HuffException("Illegal header starting with:" + " " + allBits);
	  HuffNode r = readHeader(i);
	  if(myDebugLevel==DEBUG_HIGH)
	  {
	    System.out.println(r);
	  }
	  readCompressedBits(r, i, o);
	  o.close();
	}
	
	private HuffNode readHeader(BitInputStream i)
	{
	  int b = i.readBits(1);
	  if(b==-1)
	    throw new HuffException("Invalid bit equalling -1");
	   if(b==0)
	   {
	     HuffNode left = readHeader(i);
	     HuffNode right = readHeader(i);
	     return(new HuffNode(0,0, left, right));
	   }
	   else
	   {
	     int nextInt = i.readBits(BITS_PER_WORD+1);
	     return(new HuffNode(nextInt, 0, null, null));
	   }
	}
	
	private void readCompressedBits(HuffNode r, BitInputStream i, BitOutputStream o)
	{
	  HuffNode current = r;
	  while(true)
	  {
	    int b = i.readBits(1);
	    if(b==-1)
	    {
	      throw new HuffException("Invalid bit equalling -1");
	    }
	    else
	    {
	      if(b==0)
	        current=current.myLeft;
	      else
	        current=current.myRight;
	      if(current.myLeft==null && current.myRight==null)
	      {
	        if(current.myValue == PSEUDO_EOF)
	          break;
	        else
	        {
	          o.writeBits(BITS_PER_WORD, current.myValue);
	          current=r;
	        }
	      }
	    }
	  }
	}
}