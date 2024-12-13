package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
* Manages temporary attributes on nodes.
*
* During processing it is convenient to add temporary attributes to various
* nodes so that later processing can determine what earlier processing has done
* to them.
*
* This *can* be done directly simply by adding the attribute.  The trouble is
* this leads to a temptation to add more and more temporary attributes, which is
* confusing; and also can't be policed: it's impossible to ensure that when
* later processing looks for an attribute, it is using precisely the right name.
*
* The present class attempts to impose some discipline.  If you play by the
* rules, you will use *only* the facilities here to add and check for these
* temporary attributes.  This ensures that the correct names are used.  And also
* it is just painful enough to add new attributes here (which you do by copying
* and modifying an existing block) ... just painful enough to discourage
* profligate use of the facility.
*
* Attributes come in two forms.  In one, we are interested purely in the
* existence or non-existence of the attribute, and the 'set' method below does
* not accept a value for the attribute (internally it uses "y").  In the other,
* the attribute may carry useful information, and in this case 'set' does allow
* you to pass a value.
*
* Internally, every node which has any temporary attributes also has an
* attribute '_t' which has the value 'y'.  This makes it easy to locate all such
* nodes, with a view to removing temporaries before writing out the XML.  And all
* temporary attributes have names which start with an underscore.
*
* @author ARA "Jamie" Jamieson
*/

object NodeMarker: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Deletes all the temporary markers.
  *
  */

  fun deleteAllMarkers (rootNode: Node)
  {
    rootNode.getAllNodesBelow().forEach { node ->
      try { Dom.getAttributes(node).keys.filter { it.startsWith("_") }.forEach { Dom.deleteAttribute(node, it) } } catch (_: Exception) {}
    }
  }



  /****************************************************************************/
  /**
  * Deletes all the temporary markers.
  *
  */

  fun deleteAllMarkers (doc: Document)
  {
    doc.getAllNodesBelow().forEach { node ->
      try { Dom.getAttributes(node).keys.filter { it.startsWith("_") }.forEach { Dom.deleteAttribute(node, it) } } catch (_: Exception) {}
    }
  }



  /****************************************************************************/
  fun deleteAddedFootnote (node: Node) = deleteTemporaryAttribute(node, C_AddedFootnote)
  fun getAddedFootnote (node: Node) = node[C_AddedFootnote]
  fun hasAddedFootnote (node: Node) = null != getAddedFootnote(node)
  fun setAddedFootnote (node: Node): NodeMarker { addTemporaryAttribute(node, C_AddedFootnote, "y"); return this }
  private const val C_AddedFootnote = "_AddedFootnote"


  /****************************************************************************/
  fun deleteCrossBoundaryMarkup (node: Node) = deleteTemporaryAttribute(node, C_CrossBoundaryMarkup)
  fun getCrossBoundaryMarkup (node: Node) = node[C_CrossBoundaryMarkup]
  fun hasCrossBoundaryMarkup (node: Node) = null != getCrossBoundaryMarkup(node)
  fun setCrossBoundaryMarkup (node: Node): NodeMarker { addTemporaryAttribute(node, C_CrossBoundaryMarkup, "y"); return this }
  private const val C_CrossBoundaryMarkup = "_CrossBoundaryMarkup"


  /****************************************************************************/
  fun deleteDummy (node: Node) = deleteTemporaryAttribute(node, C_Dummy)
  fun getDummy (node: Node) = node[C_Dummy]
  fun hasDummy (node: Node) = null != getDummy(node)
  fun setDummy (node: Node): NodeMarker { addTemporaryAttribute(node, C_Dummy, "y"); return this }
  const val C_Dummy = "_Dummy"


  /****************************************************************************/
  fun deleteElisionType (node: Node) = deleteTemporaryAttribute(node, C_ElisionType)
  fun getElisionType (node: Node) = node[C_ElisionType]
  fun hasElisionType (node: Node) = null != getElisionType(node)
  fun setElisionType (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_ElisionType, value); return this }
  private const val C_ElisionType = "_ElisionType"


  /****************************************************************************/
  fun deleteEmptyVerseType (node: Node) = deleteTemporaryAttribute(node, C_EmptyVerseType)
  fun getEmptyVerseType (node: Node) = node[C_EmptyVerseType]
  fun hasEmptyVerseType (node: Node) = null != getEmptyVerseType(node)
  fun setEmptyVerseType (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_EmptyVerseType, value); return this }
  private const val C_EmptyVerseType = "_EmptyVerseType"


  /****************************************************************************/
  fun deleteGeneratedReason (node: Node) = deleteTemporaryAttribute(node, C_GeneratedReason)
  fun getGeneratedReason (node: Node) = node[C_GeneratedReason]
  fun hasGeneratedReason (node: Node) = null != getGeneratedReason(node)
  fun setGeneratedReason (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_GeneratedReason, value); return this }
  private const val C_GeneratedReason = "_GeneratedReason"


  /****************************************************************************/
  fun deleteMasterForElisionOfLength (node: Node) = deleteTemporaryAttribute(node, C_MasterForElisionOfLength)
  fun getMasterForElisionOfLength (node: Node) = node[C_MasterForElisionOfLength]
  fun hasMasterForElisionOfLength (node: Node) = null != getMasterForElisionOfLength(node)
  fun setMasterForElisionOfLength (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_MasterForElisionOfLength, value); return this }
  private const val C_MasterForElisionOfLength = "_MasterForElisionOfLength"


 /****************************************************************************/
  fun deleteMoveNoteToStartOfVerse (node: Node) = deleteTemporaryAttribute(node, C_MoveNoteToStartOfVerse)
  fun getMoveNoteToStartOfVerse (node: Node) = node[C_MoveNoteToStartOfVerse]
  fun hasMoveNoteToStartOfVerse (node: Node) = null != getMoveNoteToStartOfVerse(node)
  fun setMoveNoteToStartOfVerse (node: Node): NodeMarker { addTemporaryAttribute(node, C_MoveNoteToStartOfVerse, "y"); return this }
  private const val C_MoveNoteToStartOfVerse = "_MoveNoteToStartOfVerse"


  /****************************************************************************/
  fun deleteOriginalId (node: Node) = deleteTemporaryAttribute(node, C_OriginalId)
  fun getOriginalId (node: Node) = node[C_OriginalId]
  fun hasOriginalId (node: Node) = null != getOriginalId(node)
  fun setOriginalId (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_OriginalId, value); return this }
  private const val C_OriginalId = "_OriginalId"


  /****************************************************************************/
  fun deleteTableOwnerType (node: Node) = deleteTemporaryAttribute(node, C_TableOwnerType)
  fun getTableOwnerType (node: Node) = node[C_TableOwnerType]
  fun hasTableOwnerType (node: Node) = null != getTableOwnerType(node)
  fun setTableOwnerType (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_TableOwnerType, value); return this }
  private const val C_TableOwnerType = "_TableOwnerType"


  /****************************************************************************/
  fun deleteTableRefKeys (node: Node) = deleteTemporaryAttribute(node, C_TableRefKeys)
  fun getTableRefKeys (node: Node) = node[C_TableRefKeys]
  fun hasTableRefKeys (node: Node) = null != getTableOwnerType(node)
  fun setTableRefKeys (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_TableRefKeys, value); return this }
  private const val C_TableRefKeys = "_TableRefKeys"


  /****************************************************************************/
  fun deleteUniqueId (node: Node) = deleteTemporaryAttribute(node, C_UniqueId)
  fun getUniqueId (node: Node) = node[C_UniqueId]
  fun hasUniqueId (node: Node) = null != getUniqueId(node)
  fun setUniqueId (node: Node, value: Int): NodeMarker { addTemporaryAttribute(node, C_UniqueId, value.toString()); return this }
  private const val C_UniqueId = "_UniqueId"





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun addTemporaryAttribute (node: Node, attributeName: String, attributeValue: String)
  {
    node[attributeName] = attributeValue
    node["_t"] = "y"
  }


  /****************************************************************************/
  private fun deleteTemporaryAttribute (node: Node, attributeName: String)
  {
    node -= attributeName
    if (!Dom.getAttributes(node).any { it.key.startsWith("_") })
      node -= "_t"
  }
}
