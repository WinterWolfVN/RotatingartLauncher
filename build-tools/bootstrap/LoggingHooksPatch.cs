using HarmonyLib;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading.Tasks;



    public class LoggingHooksPatch
    {
        public static void LoggingHooksHarmonyPatch(string assembly)
        {

            // Load the external assembly dynamically (you need to specify the correct path to the external assembly)
            Assembly externalAssembly = Assembly.LoadFrom(assembly);

            // Get the type for LoggingHooks from the external assembly
            System.Type loggingHooksType = externalAssembly.GetType("Terraria.ModLoader.Engine.LoggingHooks");

            if (loggingHooksType == null)
            {
                Console.WriteLine("LoggingHooks class not found in the external assembly.");
                return;
            }

            // Get the MethodInfo for the method you want to patch
            MethodInfo originalMethod = loggingHooksType.GetMethod("Init", BindingFlags.Static | BindingFlags.NonPublic);


            // Create a Harmony instance
            Harmony harmony = new Harmony("com.example.patch");

            // Create the HarmonyMethod for the prefix (empty method)
            HarmonyMethod prefix = new HarmonyMethod(typeof(LoggingHooksPatch), "loggingPatchh_Prefix");

            // Apply the patch
            harmony.Patch(originalMethod, prefix);

            Console.WriteLine("Patch applied successfully!");
        }
        public static bool loggingPatchh_Prefix()
        {
            Console.WriteLine("FixBrokenConsolePipeError method is now a no-op.");
            return false; // Skip the original method and prevent its execution
        }
    }

