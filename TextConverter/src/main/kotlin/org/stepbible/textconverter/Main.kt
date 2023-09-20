/**********************************************************************************************************************/
package org.stepbible.textconverter

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.debug.Dbg
import org.stepbible.textconverter.support.debug.Logger



/**********************************************************************************************************************/
/**
 * The converter main program.
 *
 * @param args Command line arguments.
 */

fun main (args: Array<String>)
{
  Dbg.setBooksToBeProcessed("Rev")

  try
  {
    mainCommon(args)
    if (!ConfigData["stepReversificationDataLocation"]!!.startsWith("http")) println(C_Local_ReversificationData)
    if (!ConfigData.getAsBoolean("stepEncryptionApplied", "no")) println(C_NotEncrypted)
    println("Finished\n")
  }
  catch (e: Exception)
  {
    println(e.message)
    e.printStackTrace()
  }
}


/******************************************************************************/
private fun mainCommon (args: Array<String>)
{
  TextConverterController().process(args)
  TestController.atEndOfProcessing()
  Logger.summariseResults()
}


/******************************************************************************/
private const val C_Local_ReversificationData ="""
     #        #######   #####      #     #            ######   #######  #     #  #######  ######    #####   ###  #######  ###   #####      #     #######  ###  #######  #     #      ######      #     #######     #    
     #        #     #  #     #    # #    #            #     #  #        #     #  #        #     #  #     #   #   #         #   #     #    # #       #      #   #     #  ##    #      #     #    # #       #       # #   
     #        #     #  #         #   #   #            #     #  #        #     #  #        #     #  #         #   #         #   #         #   #      #      #   #     #  # #   #      #     #   #   #      #      #   #  
     #        #     #  #        #     #  #            ######   #####    #     #  #####    ######    #####    #   #####     #   #        #     #     #      #   #     #  #  #  #      #     #  #     #     #     #     # 
     #        #     #  #        #######  #            #   #    #         #   #   #        #   #          #   #   #         #   #        #######     #      #   #     #  #   # #      #     #  #######     #     ####### 
     #        #     #  #     #  #     #  #            #    #   #          # #    #        #    #   #     #   #   #         #   #     #  #     #     #      #   #     #  #    ##      #     #  #     #     #     #     # 
     #######  #######   #####   #     #  #######      #     #  #######     #     #######  #     #   #####   ###  #        ###   #####   #     #     #     ###  #######  #     #      ######   #     #     #     #     # 
                                                                                                                                                                                                                    
     """


/******************************************************************************/
private const val C_NotEncrypted = """
      _   _    ___    _____     _____   _   _    ____   ____   __   __  ____    _____   _____   ____
     | \ | |  / _ \  |_   _|   | ____| | \ | |  / ___| |  _ \  \ \ / / |  _ \  |_   _| | ____| |  _ \
     |  \| | | | | |   | |     |  _|   |  \| | | |     | |_) |  \ V /  | |_) |   | |   |  _|   | | | |
     | |\  | | |_| |   | |     | |___  | |\  | | |___  |  _ <    | |   |  __/    | |   | |___  | |_| |
     |_| \_|  \___/    |_|     |_____| |_| \_|  \____| |_| \_\   |_|   |_|       |_|   |_____| |____/
                     
     """


/******************************************************************************/
/* Convenient place to lodge small tests which don't need config. */

private fun test ()
{
/*
      println(convertRepeatingStringToNumber("a", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertRepeatingStringToNumber("z", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertRepeatingStringToNumber("aa", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertRepeatingStringToNumber("az", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertRepeatingStringToNumber("ba", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertRepeatingStringToNumber("bz", 'a'.code, 'z'.code - 'a'.code + 1))
      println(convertNumberToRepeatingString(1, 'a', 'z'))
      println(convertNumberToRepeatingString(26, 'a', 'z'))
      println(convertNumberToRepeatingString(27, 'a', 'z'))
      println(convertNumberToRepeatingString(52, 'a', 'z'))
      println(convertNumberToRepeatingString(53, 'a', 'z'))
      println(convertNumberToRepeatingString(78, 'a', 'z'))
*/
}
