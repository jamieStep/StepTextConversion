package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.debug.Logger
import org.stepbible.textconverter.utils.X_DataCollection
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Common definitions for all processing subelements.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class SE (dataCollection: X_DataCollection)
{
  /****************************************************************************/
  fun requireHasRun    (requireHasRun   : List<Class<*>>) = SE_Registry.requireHasRun   (this::class.java, requireHasRun)
  fun requireHasRun    (requireHasRun   : Class<*>)       = SE_Registry.requireHasRun   (this::class.java, listOf(requireHasRun))
  fun requireHasNotRun (requireHasNotRun: List<Class<*>>) = SE_Registry.requireHasNotRun(this::class.java, requireHasNotRun)
  fun requireHasNotRun (requireHasNotRun: Class<*>)       = SE_Registry.requireHasNotRun(this::class.java, listOf(requireHasNotRun))



  /****************************************************************************/
  fun doNothing () { }
  open fun process () = m_DataCollection.getRootNodes().forEach { process(it) }
  abstract fun process (rootNode: Node)



  /****************************************************************************/
  init {
    SE_Registry.registerAsHavingRun(this::class.java)
  }



  /****************************************************************************/
  protected val m_DataCollection = dataCollection
  protected val m_FileProtocol = dataCollection.getFileProtocol()
}





/******************************************************************************/
/**
* Used to let things check they're being run in an appropriate order.
*/

object SE_Registry
{
  /****************************************************************************/
  fun registerAsHavingRun (c: Class<*>) = m_ThingsWhichHaveAlreadyRun.add(c.simpleName)


  /****************************************************************************/
  fun requireHasNotRun (me: Class<*>, requireHasNotRun: List<Class<*>>)
  {
    val bads = requireHasNotRun.filter { it.simpleName in m_ThingsWhichHaveAlreadyRun }
    if (bads.isNotEmpty())
    {
      Logger.error("${me.simpleName} requires ${requireHasNotRun.joinToString(", "){ it.simpleName }} should not have run already, but they have.")
      Logger.announceAll(true)
    }
  }


  /****************************************************************************/
  fun requireHasRun (me: Class<*>, requireHasRun: List<Class<*>>)
  {
    val bads = requireHasRun.filterNot { it.simpleName in m_ThingsWhichHaveAlreadyRun }
    if (bads.isNotEmpty())
    {
      Logger.error("${me.simpleName} requires ${requireHasRun.joinToString(", "){ it::class.simpleName!! }} to have run already, and they have not.")
      Logger.announceAll(true)
    }
  }


  /****************************************************************************/
    val m_ThingsWhichHaveAlreadyRun: MutableList<String> = mutableListOf()
}