using HarmonyLib;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;




    public class TryFixFileCasings
{
        public static void TryFixFileCasingsPatch(string assembly)
        {

        

            Assembly externalAssembly = Assembly.LoadFrom(assembly);

            // Get the type for LoggingHooks from the external assembly
            System.Type TMLContentManagerType = externalAssembly.GetType("Terraria.ModLoader.Engine.TMLContentManager");

            if (TMLContentManagerType == null)
            {
                Console.WriteLine("LoggingHooks class not found in the external assembly.");
                return;
            }

            // Get the MethodInfo for the method you want to patch
            MethodInfo originalMethod = TMLContentManagerType.GetMethod("TryFixFileCasings", BindingFlags.Static | BindingFlags.NonPublic);


            // Create a Harmony instance
            Harmony harmony = new Harmony("com.example.patch");

            // Create the HarmonyMethod for the prefix (empty method)
            HarmonyMethod prefix = new HarmonyMethod(typeof(TryFixFileCasings), "TryFixFileCasingsPatch_Prefix");

            // Apply the patch
            harmony.Patch(originalMethod, prefix);


         

            Console.WriteLine("TMLContentManagerPatch applied successfully!");
        }
        public static bool TryFixFileCasingsPatch_Prefix()
        {
         
            return false;
        }

    }

