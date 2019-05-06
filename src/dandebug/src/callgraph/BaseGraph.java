/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package callgraph;

import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxGraphView;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingConstants;

/**
 *
 * @author zzk
 */
public class BaseGraph<NodeType> {
  private mxGraph                               baseGraph = new mxGraph();
  private Map<NodeType, Object>                 vertexMap = new HashMap<>();
  private Map<NodeType, Map<NodeType, Object>>  edgeMap = new HashMap<>();
  private int                                   vertexCount = 0;
  private int                                   edgeCount = 0;
  
  final public mxGraph getGraph() {
    return this.baseGraph;
  }
  
  final public int getVertexCount() {
    return this.vertexCount;
  }
  
  final public Map<NodeType, Object> getVertexMap(){
      return this.vertexMap;
  }
  
  final public Object addVertex(NodeType node, String content) {
    this.baseGraph.getModel().beginUpdate();
    Object parent = this.baseGraph.getDefaultParent();
    Object vertex = this.baseGraph.insertVertex(parent, null, content, 0, 0, 90, 30, "fontColor=black");
    this.vertexMap.put(node, vertex);
    this.baseGraph.getModel().endUpdate();
    vertexCount++;
    return vertex;
  }
  
  final public Object getVertex(NodeType node) {
    return this.vertexMap.get(node);
  }
  
  final public int getEdgeCount() {
    return this.edgeCount;
  }
  
  final public Object addEdge(NodeType nodeSrc, NodeType nodeDst, String content) {
    Object srcVertex = getVertex(nodeSrc);
    if (srcVertex == null)
      return null;
    
    Object dstVertex = getVertex(nodeDst);
    if (dstVertex == null)
      return null;
    
    this.baseGraph.getModel().beginUpdate();
    Object parent = this.baseGraph.getDefaultParent();
    Object edge = this.baseGraph.insertEdge(parent, null, content, srcVertex, dstVertex);
    Map<NodeType, Object> edgeMap = this.edgeMap.get(nodeSrc);
    if (edgeMap == null) {
      edgeMap = new HashMap<>();
      this.edgeMap.put(nodeSrc, edgeMap);
    }
    edgeMap.put(nodeDst, edge);
    this.baseGraph.getModel().endUpdate();
    this.edgeCount++;
    return edge;
  }
  
  final public Object addDash(NodeType nodeSrc, NodeType nodeDst, String content) {
    Object srcVertex = getVertex(nodeSrc);
    if (srcVertex == null)
      return null;
    
    Object dstVertex = getVertex(nodeDst);
    if (dstVertex == null)
      return null;
    
    this.baseGraph.getModel().beginUpdate();
    Object parent = this.baseGraph.getDefaultParent();
    Object edge = this.baseGraph.insertEdge(parent, null, content, srcVertex, dstVertex, "dashed=1");
    Map<NodeType, Object> edgeMap = this.edgeMap.get(nodeSrc);
    if (edgeMap == null) {
      edgeMap = new HashMap<>();
      this.edgeMap.put(nodeSrc, edgeMap);
    }
    edgeMap.put(nodeDst, edge);
    this.baseGraph.getModel().endUpdate();
    this.edgeCount++;
    return edge;
  }
  
  final public Object getEdge(NodeType nodeSrc, NodeType nodeDst) {
    Map<NodeType, Object> edgeMap = this.edgeMap.get(nodeSrc);
    if (edgeMap == null)
      return null;
    return edgeMap.get(nodeDst);
  }
  
  final public void colorVertex(NodeType node, String color) {
    this.baseGraph.getModel().beginUpdate();
    mxGraphView view = this.baseGraph.getView();
    Object vertex = getVertex(node);
    if (vertex == null)
      return;
    mxCellState vertexState = view.getState(vertex);
    vertexState.getStyle().put("fillColor", color);
    this.baseGraph.getModel().endUpdate();
  }
  
  /*added by Madeline Sgro 7/6/2017
  scales the text of a given node based on a scale factor
  */
  final public void scaleVertex(NodeType node, double scaleFactor){
    this.baseGraph.getModel().beginUpdate();
    mxGraphView view = this.baseGraph.getView();
    Object vertex = getVertex(node);
    if(vertex == null){
      return;
    }
    view.setScale(scaleFactor); 
    this.baseGraph.getModel().endUpdate();
  }
  
  /*added by Madeline Sgro 08/02/2017
  scales partial call graph
  */
  final public void scaleVerticies(double scaleFactor){
    this.baseGraph.getModel().beginUpdate();
    mxGraphView view = this.baseGraph.getView();
    view.setScale(scaleFactor);
    this.baseGraph.getModel().endUpdate();
  }
  
  final public NodeType getSelectedNode() {
    Object vertex = this.baseGraph.getSelectionCell();
    for (Map.Entry<NodeType, Object> vertexMapEnt : vertexMap.entrySet())
      if (vertexMapEnt.getValue() == vertex)
        return vertexMapEnt.getKey();
    return null;
  }
  
  final public void layoutGraph() {
    if (this.edgeCount <= 5000) {
      mxHierarchicalLayout layout = new mxHierarchicalLayout(this.baseGraph);
      layout.setOrientation(SwingConstants.VERTICAL);
      layout.execute(this.baseGraph.getDefaultParent());
    } else {
      //mxCompactTreeLayout layout = new mxCompactTreeLayout(this.callGraph);
      mxFastOrganicLayout layout = new mxFastOrganicLayout(this.baseGraph);
      layout.execute(this.baseGraph.getDefaultParent());
    }
    
    this.baseGraph.setDisconnectOnMove(false);
  }
}
