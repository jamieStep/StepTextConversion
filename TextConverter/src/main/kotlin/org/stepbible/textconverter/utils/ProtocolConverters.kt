package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.miscellaneous.*
import org.w3c.dom.Document
import org.w3c.dom.Node

/******************************************************************************/
/**
* Phase 1 processing is responsible for supplying us with OSIS in a form which
* could be passed to third parties.
*
* (Almost.  OSIS requires that lists and poetry be encapsulated in tags
* equivalent to HTML's ul.  It's difficult to do this because there is no USX
* equivalent, and it gets in the way of processing and actually makes the
* rendered appearance _worse_.  Given that things seem to work perfectly well
* without them, I don't bother with them.  To this extent, therefore, the OSIS
* may not comply with standards.)
*
* Aside from that caveat, we would be able to supply this OSIS to third parties
* if we wished to.
*
* However, this format is not very amenable to further processing.  To this
* end, [ProtocolConverterOsisForThirdPartiesToInternalOsis] converts it to a
* more useful form (and should therefore be used early in the processing).
*
* @author ARA "Jamie" Jamieson
*/

class ProtocolConverters // Just here to give the documentation processor something to latch on to -- isn't intended to be used by anything.



/******************************************************************************/
/**
* See [ProtocolConverters].
*
* @author ARA "Jamie" Jamieson
*/

object ProtocolConverterOsisForThirdPartiesToInternalOsis
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun process (dataCollection: X_DataCollection)
  {
    /**************************************************************************/
    val doc = dataCollection.getDocument()



    /**************************************************************************/
    /* This one needs to be undone again.  It's more convenient to locate book
       nodes via a tag name rather than via an attribute value, so I change
       the tag name here, but the result isn't valid OSIS, so it will need to
       be changed back before we do anything 'official' with the OSIS. */

    doc.findNodesByAttributeValue("div", "type", "book").forEach { Dom.setNodeName(it, "book") }



    /**************************************************************************/
    /* This can be left as-is.  div/type=chapter and <chapter> are synonyms in
       OSIS, but the <chapter> version is more convenient. */

    doc.findNodesByAttributeValue("div", "type", "chapter").forEach { Dom.setNodeName(it, "chapter") }



    /**************************************************************************/
    /* OSIS supports (or strictly, _requires_) that bullet-point lists and
       poetry tags be wrapped in enclosing tags.  These get in the way of other
       processing and don't actually seem to be relevant to whether things work
       or not, so it's convenient to ditch them. */

    doc.findNodesByName("lg").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) }
  }
}
