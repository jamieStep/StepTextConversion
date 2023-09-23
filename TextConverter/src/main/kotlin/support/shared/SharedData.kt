/******************************************************************************/
package org.stepbible.textconverter.support.shared

import org.stepbible.textconverter.support.ref.RefKey


/******************************************************************************/
/**
 * Shared data intended to exist throughout the lifetime of the processing
 * chain.
 *
 * @author ARA "Jamie" Jamieson
 */

object SharedData
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/
  
  /****************************************************************************/
  /* Indicates whether we have the necessary information to parse vernacular
     references.  (I assume that if we can parse them, we will also be able to
     generate them if necessary.) */
  
  var CanParseVernacularReferences = false
  
  
  /****************************************************************************/
  /**
   * Used to permit various parts of the processing to record any special
   * features of the text which may turn out to be worth reporting.
   */
  
  var SpecialFeatures = FeatureIdentifier()
  
  
  /****************************************************************************/
  /**
   * Used to track verses which have been altered by reversification.  This is
   * used in turn to supply information for the copyright page and for the
   * Sword config file.
   */
  
  var VersesAmendedByReversification: MutableMap<RefKey, RefKey> = mutableMapOf()

  
  
  /****************************************************************************/
  /**
   * Used to track verses to which reversification has added footnotes, but
   * which are otherwise unchanged.  This may be used in turn to supply
   * information for the copyright page and for the Sword config file.
   */
  
  var VersesToWhichReversificationSimplyAddedNotes: MutableMap<RefKey, RefKey> = mutableMapOf()
  

  
  /****************************************************************************/
  /* The processing works out a) which versification scheme best fits the text
     and b) how well NRSV(A) fits. */
  
  var BestVersificationScheme: String? = null
  var BestVersificationSchemeScore: Int? = null
  var NrsvVersificationScheme: String? = null
  var NrsvVersificationSchemeScore: Int? = null
  var NrsvVersificationSchemeNumberOfExcessVerseEquivalentsInOsisScheme: Int? = null
  var NrsvVersificationSchemeNumberOfMissingVerseEquivalentsInOsisScheme: Int? = null
}