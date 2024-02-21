package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.stepexception.StepExceptionShouldHaveBeenOverridden
import org.stepbible.textconverter.utils.ProcessRegistry
import org.stepbible.textconverter.utils.X_DataCollection
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Common definitions for all processing subelements.
 *
 * In addition to defining processDataCollectionInternal and / or
 * processRootNodeInternal (which are needed to support the normal means of
 * using derived classes -- although there is nothing to prevent you from
 * defining additional public methods in derived classes and using those
 * directly) ... in addition to that, you must also override myPrerequisites
 * and thingsIveDone.
 *
 * The former should give a list of ProcessRegistry.Functionality objects
 * represent functionality which you require either definitely to have run
 * or definitely not to have run before the class from the present one does
 * its stuff.  You can also give this as null if you don't require any checks.
 *
 * And thingsIveDone should give a list of ProcessRegistry.Functionality objects
 * represent stuff the present class.  This is reported here.  If you don't wish
 * to report anything, give it as null.  Also give it as null if your processing
 * needs to decide which things to report -- give it thingsIveDone as null,
 * and then report the things you do individually from within your code.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class SE (dataCollection: X_DataCollection)
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
  * Processes the entire data collection.
  */

  fun processDataCollection ()
  {
    m_DataCollection.getProcessRegistry().checkPrerequisites(this, myPrerequisites())
    processDataCollectionInternal()
    m_DataCollection.getProcessRegistry().iHaveDone(this, thingsIveDone())
  }


  /****************************************************************************/
  /**
  * Processes each book root in the data collection.
  */

  fun processAllRootNodes () = m_DataCollection.getRootNodes().forEach { processRootNode(it) }


  /****************************************************************************/
  /**
  * Processes a single book root node.
  *
  * @param rootNode
  */

  fun processRootNode (rootNode: Node)
  {
    m_DataCollection.getProcessRegistry().checkPrerequisites(this, myPrerequisites())
    processRootNodeInternal(rootNode)
    m_DataCollection.getProcessRegistry().iHaveDone(this, thingsIveDone())
  }


  /****************************************************************************/
  protected open fun myPrerequisites (): List<ProcessRegistry.Functionality> = listOf(ProcessRegistry.NoSignificantRequirements)
  protected open fun thingsIveDone (): List<ProcessRegistry.Functionality> = listOf(ProcessRegistry.NothingSignificantToReport)
  protected open fun processDataCollectionInternal (): Unit = throw StepExceptionShouldHaveBeenOverridden()
  protected open fun processRootNodeInternal (rootNode: Node): Unit = throw StepExceptionShouldHaveBeenOverridden()





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  protected val m_DataCollection = dataCollection
  protected val m_FileProtocol = dataCollection.getFileProtocol()
}
