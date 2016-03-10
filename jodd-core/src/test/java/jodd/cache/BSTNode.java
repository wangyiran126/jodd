package jodd.cache;

/**
 * Created by wangyiran on 21/2/2016.
 */
public class BSTNode {
    public int data;
    public BSTNode parent;
    public BSTNode left;
    public BSTNode right;

    public BSTNode(int data)
    {
        this.data = data;
        this.left = null;
        this.right = null;
        this.parent = null;
    }

    public BSTNode()
    {
    }
}
