package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Node

/******************************************************************************/
/**
* A collection of classes which are used early on in order to get a given
* file or collection of files into a form more amenable to further processing.
*
* At the time of writing the only derived class handles OSIS.
*
* @author ARA "Jamie" Jamieson
*/

interface BasicProtocolTweaker
{
  /****************************************************************************/
  /**
  * Applies the various changes.  This method may make changes which can be
  * left in place permanently, and / or changes which have to be undone
  * again (via [unprocess]).
  *
  * @param dataCollection The collection to be processed.
  */

  fun process (dataCollection: X_DataCollection)


  /****************************************************************************/
  /**
  * Undoes any of the changes made by [process] which need to be undone.
  *
  * @param dataCollection The collection to be processed.
  */

  fun unprocess (dataCollection: X_DataCollection) {}
}



/******************************************************************************/
/**
* See [BasicProtocolTweaker].
*
*
*
* @author ARA "Jamie" Jamieson
*/

object Osis_BasicTweaker: BasicProtocolTweaker, ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                 Public                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Carries out any general simplifications to make subsequent processing
  * easier.
  *
  * @param dataCollection
  */

  override fun process (dataCollection: X_DataCollection)
  {
    with(ParallelRunning(true)) {
      run {
        dataCollection.getRootNodes().forEach { rootNode ->
          asyncable { Osis_BasicTweakerPerBook().processRootNode(rootNode) }
        } // forEach
      } // run
    } // with
  } // fun
}




private class Osis_BasicTweakerPerBook
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  fun processRootNode (rootNode: Node)
  {
    process_lg(rootNode)
  }


  /****************************************************************************/
  /* OSIS supports (or strictly, _requires_) that bullet-point lists and
     poetry tags be wrapped in enclosing tags.  These get in the way of other
     processing and don't actually seem to be relevant to whether things work
     or not, so it's convenient to ditch them. */

  private fun process_lg (rootNode: Node) = rootNode.findNodesByName("lg").forEach { Dom.promoteChildren(it); Dom.deleteNode(it) }
}
