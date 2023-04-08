# LBS application for PoC 

LBS (Location-based Services) have become mainstream and are used by the average mobile user almost daily.
Immediate interaction with an LBS server leaks the searching node's identity and thus causes privacy leakage.
This privacy can be preserved by forwarding the request to some other mobile node and having that node
interact with the LBS server directly. The answer from the LBS server can then be forwarded back to the
initial node.

This application serves the purpose of showcasing how two mobile devices within the same LAN network
(which in the future can be generalized as a WAN or even the Internet) can communicate with each other
for the above purpose.

Therefore we have 2 modes for this application:
1) The searching node (i.e. the node that wants to search for something)
2) The proxy (intermediate node) that will interact with the LBS directly and forward back the answer to the searching node.
