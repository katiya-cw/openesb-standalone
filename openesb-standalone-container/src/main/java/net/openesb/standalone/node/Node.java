package net.openesb.standalone.node;

/**
 *
 * @author David BRASSELY (brasseld at gmail.com)
 * @author OpenESB Community
 */
public interface Node {

    /**
     * Start the node. If the node is already started, this method is no-op.
     */
    void start();

    /**
     * Stops the node. If the node is already stopped, this method is no-op.
     */
    void stop();
    
    /**
     * Returns the node name.
     * @return  The node name.
     */
    String name();
}
