package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.stepexception.StepException

/******************************************************************************/
/**
 * Supports run-time checks on process ordering.  Callers request a check that
 * certain functionality should (or should not) have run before they run, and
 * also register what things they have done.
 *
 * In general I suppose all that is necessary is that things report doing
 * things that 'matter' to other processing.  If a class does something
 * which nothing else relies upon, there is no real need to record the fact that
 * it has happened.  Nonetheless I've tended to the view that amongst the
 * SE-based classes (which is what this is primarily aimed at), each should
 * at least register the fact that it has run.
 *
 * You're free to add to the list of Functionality-based classes so as to
 * record additional things which may have happened -- just make sure each
 * one has a unique bit setting (and don't use the very top bit -- I need
 * that for other purposes).
 *
 * Strictly this should be rather needless.  However, the dependencies are
 * complicated enough that it is easy to get ordering wrong; and even if we
 * were to get it right, I think the present class represents a useful
 * discipline, forcing me to think about ordering issues.
 *
 * @author ARA "Jamie" Jamieson
*/

class ProcessRegistry
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  open class Functionality (private val bitSetting: Long, val caller: String?)
  {
    operator fun not () = Functionality(bitSetting.inv(), null)
    val bitMap = bitSetting

    init {
      if (null != caller)
        m_Map[bitSetting] = caller
    }

    companion object {
      val m_Map: MutableMap<Long, String> = mutableMapOf()
      fun getName (bitMap: Long) = m_Map[bitMap] ?: "Don't Know"
    }
  }

  // You can use these two to give meaningful names where nothing much is required / happening.
  object NoSignificantRequirements            : Functionality(0x0000_0000_0000_0000, null)
  object NothingSignificantToReport           : Functionality(0x0000_0000_0000_0000, null)

   object BasicValidation                      : Functionality(0x0000_0000_0000_0001, "BasicValidation")
   object CalloutsStandardised                 : Functionality(0x0000_0000_0000_0002, "CalloutsStandardised")
   object CanonicalHeadingsCanonicalised       : Functionality(0x0000_0000_0000_0004, "CanonicalHeadingsCanonicalised")
   object CrossBoundaryMarkupSimplified        : Functionality(0x0000_0000_0000_0008, "CrossBoundaryMarkupSimplified")
   object DetailedContentValidated             : Functionality(0x0000_0000_0000_0010, "DetailedContentValidated")
   object ElisionsExpanded                     : Functionality(0x0000_0000_0000_0020, "ElisionsExpanded")
   object FeatureDataCollected                 : Functionality(0x0000_0000_0000_0040, "FeatureDataCollected")
   object ListsEncapsulated                    : Functionality(0x0000_0000_0000_0080, "ListsEncapsulated")
   object RuntimeReversificationHandled        : Functionality(0x0000_0000_0000_0100, "RuntimeReversificationHandled")
   object StrongsCanonicalised                 : Functionality(0x0000_0000_0000_0200, "StrongsCanonicalised")
   object SubversesCollapsed                   : Functionality(0x0000_0000_0000_0400, "SubversesCollapsed")
   object TablesRestructured                   : Functionality(0x0000_0000_0000_0800, "TablesRestructured")
   object EnhancedVerseEndPositioning          : Functionality(0x0000_0000_0000_1000, "EnhancedVerseEndPositioning")
   object ConversionTimeReversificationHandled : Functionality(0x0000_0000_0000_2000, "ConversionTimeReversificationHandled")
   object VerseMarkersReducedToSidsOnly        : Functionality(0x0000_0000_0000_4000, "VerseMarkersReducedToSidsOnly")
   object BasicVerseEndPositioning             : Functionality(0x0000_0000_0000_8000, "BasicVerseEndPositioning")

   object Reversification                      : Functionality(RuntimeReversificationHandled.bitMap.or(ConversionTimeReversificationHandled.bitMap), null)


  /****************************************************************************/
  /**
  * Checks that the necessary things have / have not run.
  *
  * @param caller: Object requiring the checks.
  * @param prerequisites: Things it does / doesn't want.
  */

  fun checkPrerequisites (caller: Any, prerequisites: List<Functionality>)
  {
    /**************************************************************************/
    fun convertToText (bitMap: Long): String
    {
      var res = ""

      var bit = 1L
      while (bit != 1L shl 63)
      {
        if (0L != bitMap.and(bit))
          res += Functionality.getName(bit) + ", "
        bit = bit shl 1
      }

      return if (res.isEmpty()) "" else res.substring(0, res.length - 2)
    }


    /**************************************************************************/
    if (prerequisites.isEmpty()) return
    val processedPrerequisites = processPrerequisites(prerequisites)
    val reliesOnBads    = processedPrerequisites.first .and(m_HasBeenDone.inv())
    val reliesOnNotBads = processedPrerequisites.second.and(m_HasBeenDone)



    /**************************************************************************/
    val reliesOnBadsMessage    = convertToText(reliesOnBads)
    val reliesOnNotBadsMessage = convertToText(reliesOnNotBads)
    var message = if (reliesOnBadsMessage.isNotEmpty()) " requires the following which have not run: $reliesOnBadsMessage.  " else ""
    message += if (reliesOnNotBadsMessage.isNotEmpty()) " requires the following have not run, when in fact they have: $reliesOnNotBadsMessage." else ""
    if (message.isNotEmpty())
      throw StepException(caller::class.simpleName!! + message)
  }


  /****************************************************************************/
  /**
  * Records functionality as having been performed.
  *
  * @param me Class which claims it has carried out the processing.
  * @param done: Functionality which has been performed, or null.
  */

  fun iHaveDone (me: Any, done: List<Functionality>)
  {
    if (done.isEmpty()) return

    for (d in done)
    {
      m_HasBeenDone = m_HasBeenDone.or(d.bitMap)
      addToWhoDidWhat(d.bitMap, listOf(me))
    }
  }


  /****************************************************************************/
  /**
   * Copies data from another ProcessRegistry.
   *
   * @param otherRegistry
   */

  fun setDoneDetails (otherRegistry: ProcessRegistry)
  {
    m_HasBeenDone = otherRegistry.m_HasBeenDone
    otherRegistry.m_WhoDidWhat.keys.forEach { addToWhoDidWhat(it, otherRegistry.m_WhoDidWhat[it]!!)}
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun addToWhoDidWhat (bitMap: Long, suppliers: List<Any>)
  {
    val x = m_WhoDidWhat[bitMap] ?: mutableListOf()
    suppliers.forEach { x.add(it); m_WhoDidWhat[bitMap] = x }
  }


  /****************************************************************************/
  private fun processPrerequisites (prerequisites: List<Functionality>): Pair<Long, Long>
  {
    var bitSettingReliesOn    = 0L
    var bitSettingReliesOnNot = 0L

    for (p in prerequisites)
    {
      val b = p.bitMap
      if (0L == b.and(1 shl 63))
        bitSettingReliesOn = bitSettingReliesOn.or(b)
      else
        bitSettingReliesOnNot = bitSettingReliesOnNot.or(b.inv())
    }

    return Pair(bitSettingReliesOn, bitSettingReliesOnNot)
  }

  private var m_HasBeenDone = 0L
  private val m_WhoDidWhat: MutableMap<Long, MutableList<Any>> = mutableMapOf() // For debug purposes only -- records a list of which classes provided particular functionality.
}




