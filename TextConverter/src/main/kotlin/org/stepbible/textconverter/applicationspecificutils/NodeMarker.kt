package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.Dom
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.get
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.minusAssign
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.set
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

object NodeMarker
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
  * Deletes all the temporary markers from the given node.  This can be called
  * on *any* node, under a book node or outside of a book.
  *
  * @param node
  *
  */

  fun deleteAllMarkers (node: Node) = Dom.getAttributes(node).keys.filter { it.startsWith("_") }.forEach { Dom.deleteAttribute(node, it) }



  /****************************************************************************/
  /**
  * Deletes all the temporary markers.  NOTE THAT THIS LOOKS *ONLY* AT BOOK
  * NODES AND THE THINGS BELOW THEM.
  *
  * @param dataCollection: Contains the document / root nodes to be dealt with.
  *
  */

  fun deleteAllMarkers (dataCollection: X_DataCollection)
  {
    dataCollection.getRootNodes().forEach { rootNode ->
      deleteAllMarkers(rootNode)
      Dom.getAllNodesBelow(rootNode).filter { null != it["_t"] }.forEach { deleteAllMarkers(it) }
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
  fun deleteDeleteMe (node: Node) = deleteTemporaryAttribute(node, C_CrossBoundaryMarkup)
  fun getDeleteMe (node: Node) = node[C_DeleteMe]
  fun hasDeleteMe (node: Node) = null != getDeleteMe(node)
  fun setDeleteMe (node: Node): NodeMarker { addTemporaryAttribute(node, C_DeleteMe, "y"); return this }
  private const val C_DeleteMe = "_DeleteMe"


  /****************************************************************************/
  fun deleteExcludeFromValidation (node: Node) = deleteTemporaryAttribute(node, C_ExcludeFromValidation)
  fun getExcludeFromValidation (node: Node) = node[C_ExcludeFromValidation]
  fun hasExcludeFromValidation (node: Node) = null != getExcludeFromValidation(node)
  fun setExcludeFromValidation (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_ExcludeFromValidation, value); return this }
  private const val C_ExcludeFromValidation = "_ExcludeFromValidation"


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
  fun deleteStandardRefKey (node: Node) = deleteTemporaryAttribute(node, C_StandardRefKey)
  fun getStandardRefKey (node: Node) = node[C_StandardRefKey]
  fun hasStandardRefKey (node: Node) = null != getStandardRefKey(node)
  fun setStandardRefKey (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_StandardRefKey, value); return this }
  private const val C_StandardRefKey = "_StandardRefKey"


  /****************************************************************************/
  fun deleteSubverseCoverage (node: Node) = deleteTemporaryAttribute(node, C_SubverseCoverage)
  fun getSubverseCoverage (node: Node) = node[C_SubverseCoverage]
  fun hasSubverseCoverage (node: Node) = null != getSubverseCoverage(node)
  fun setSubverseCoverage (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_SubverseCoverage, value); return this }
  private const val C_SubverseCoverage = "_SubverseCoverage"


  /****************************************************************************/
  fun deleteTableOwnerType (node: Node) = deleteTemporaryAttribute(node, C_CrossBoundaryMarkup)
  fun getTableOwnerType (node: Node) = node[C_TableOwnerType]
  fun hasTableOwnerType (node: Node) = null != getTableOwnerType(node)
  fun setTableOwnerType (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_TableOwnerType, value); return this }
  private const val C_TableOwnerType = "_TableOwnerType"


  /****************************************************************************/
  fun deleteUniqueId (node: Node) = deleteTemporaryAttribute(node, C_UniqueId)
  fun getUniqueId (node: Node) = node[C_UniqueId]
  fun hasUniqueId (node: Node) = null != getUniqueId(node)
  fun setUniqueId (node: Node, value: Int): NodeMarker { addTemporaryAttribute(node, C_UniqueId, value.toString()); return this }
  private const val C_UniqueId = "_UniqueId"



  /****************************************************************************/
  /* I _think_ I'm correct in saying that all reversification-related
     temporaries are used purely within conversion-time reversification and
     should be removed there when no longer needed.  Even if not removed, I
     don't think they're of interest outside of reversification. */
  /****************************************************************************/

  /****************************************************************************/
  fun deleteReversificationChildPos (node: Node) = deleteTemporaryAttribute(node, C_ReversificationChildPos)
  fun getReversificationChildPos (node: Node) = node[C_ReversificationChildPos]
  fun hasReversificationChildPos (node: Node) = null != getReversificationChildPos(node)
  fun setReversificationChildPos (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_ReversificationChildPos, value); return this }
  private const val C_ReversificationChildPos = "_ReversificationChildPos"


  /****************************************************************************/
  fun deleteReversificationLastEid (node: Node) = deleteTemporaryAttribute(node, C_ReversificationLastEid)
  fun getReversificationLastEid (node: Node) = node[C_ReversificationLastEid]
  fun hasReversificationLastEid (node: Node) = null != getReversificationLastEid(node)
  fun setReversificationLastEid (node: Node, value: String): NodeMarker { addTemporaryAttribute(node, C_ReversificationLastEid, value); return this }
  private const val C_ReversificationLastEid = "_ReversificationLastEid"





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
