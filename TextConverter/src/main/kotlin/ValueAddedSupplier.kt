package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import java.util.*

/******************************************************************************/
/**
 * A base class used by value-added suppliers, along with a companion class to
 * enable the details to be passed on elsewhere.
 *
 * Arguably, anything we do with the converter is adding value, in that in
 * general we are taking something like USX, which is not inherently very
 * useful to an end-user, and producing a Sword module, which is.
 *
 * However, there are certain activities -- such as applying extending tagging
 * so as to introduce morphology details -- which we regard as being
 * particularly added-value-ish.  By which I mean really that we feel the need
 * to mention that we've applied them, not so much to claim credit as to admit
 * responsibility in case we get anything wrong.
 *
 * This present class provides a uniform interface by which classes which add
 * this kind of value may record what they have done in a standard form, and
 * then makes this added value information available to those contexts which
 * might want to record it -- most particularly the administrative comments
 * which we record at the top of Sword config files, and the StepAbout setting
 * which is used to generate information for the copyright pages displayed in
 * STEP.
 *
 * @author ARA Jamieson
 */

abstract class ValueAddedSupplier
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Protected                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Supplies a list of details appropriate for use as part of the StepAbout
  * setting.
  *
  * @return Details.
  */

  protected abstract fun detailsForStepAbout (): List<String>?


  /****************************************************************************/
  /**
  * Supplies a list of details appropriate for use as part of the Sword
  * config file administrative details.
  *
  * @return Details.
  */

  protected abstract fun detailsForSwordConfigFileComments (): List<String>?



  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                             Companion                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  companion object
  {
    /**************************************************************************/
    /**
    * All value-added suppliers need to register here to flag the fact that
    * they have been invoked.
    *
    * @param name An (arbitrary) name by which the supplier can be known,
    *             should that prove useful.
    *
    * @param instance The supplier instance.
    */

    fun register (name: String, instance: ValueAddedSupplier)
    {
      m_AddedValueSuppliers[name] = instance
    }


    /**************************************************************************/
    /**
    * Does what it says on the tin.
    *
    * @return Details (may be empty).
    */

    fun getConsolidatedDetailsForStepAbout (): String
    {
      val x = m_AddedValueSuppliers.mapNotNull { (_, instance) -> instance.detailsForStepAbout() } .flatten()
      return if (x.isEmpty()) "" else x.joinToString("; ")
    }


    /**************************************************************************/
    /**
    * Does what it says on the tin.
    *
    * @return Details (may be null).
    */

    fun getConsolidatedDetailsForSwordConfigFileComments (): String?
    {
      val x = m_AddedValueSuppliers.mapNotNull { (_, instance) -> instance.detailsForSwordConfigFileComments() } .flatten()
      return if (x.isEmpty()) null else x.joinToString(prefix = "# ", separator = "\n#!", postfix = "\n#")
    }


    /**************************************************************************/
    private val m_AddedValueSuppliers = TreeMap<String, ValueAddedSupplier>(String.CASE_INSENSITIVE_ORDER)
  }
}