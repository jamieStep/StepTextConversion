/****************************************************************************/
package org.stepbible.textconverter.support.ref

import org.stepbible.textconverter.support.stepexception.StepException


/****************************************************************************/
/**
 * A rather boring base class -- simply acts as a common parent of individual
 * references and reference ranges, each of which can be an element of a
 * RefCollection.
 *
 * @author ARA "Jamie" Jamieson
 */

abstract class RefCollectionPart : RefBase()