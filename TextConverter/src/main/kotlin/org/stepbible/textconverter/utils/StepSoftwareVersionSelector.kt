package org.stepbible.textconverter.utils

import org.stepbible.textconverter.support.configdata.ConfigData

/****************************************************************************/
/**
 * Determines which version of STEP software is required to support the
 * module being worked on.
 *
 * Until the time of writing, we haven't really had any call to worry about
 * this.  However, we are now creating some modules which will work only with
 * amended STEP-specific runtime DLLs etc.  We have therefore arbitrarily
 * designated modules which work with the original software as requiring
 * version 1, and those requiring our new software as needing version 2.
 *
 * The one method in this class will need amending if we need different
 * versions in future.  (This is particularly -- or perhaps solely -- an issue
 * for offline STEP, which will have particular versions of the DLLs baked into
 * it.)
 *
 * I have hived this decision off to a separate class in the hope that the
 * slight additional prominence this gives it will increase the chances we
 * remember to maintain it.
 *
 * @author ARA "Jamie" Jamieson
 */

object StepSoftwareVersionSelector
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
   * Determines the STEP software version required.
   *
   */

  fun setStepSoftwareVersionRequired ()
  {
    // Add extra steps as necessary.  Always select the largest value.

    var res = 1 // Default if using Crosswire osis2mod and no other special features.

    if ("step" == ConfigData["stepOsis2modType"]!!) res = 2

    ConfigData["stepSoftwareVersionRequired"] = res.toString()
  }
}