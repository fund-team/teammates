package teammates.logic.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CommonData {

     static boolean[] branchReached = new boolean[24];

     public static void printVisitedBranches() {

          int nbBranchesCovered = 0;
          try {
               File file = new File("branchesCoveredInPopulateFieldsToGenerateInQuestion.txt");
               file.createNewFile();
               PrintWriter writer = new PrintWriter(file);
               for (int i = 0; i < branchReached.length; i++) {
                    writer.println("Reached Branch ID: " + i + ": " + branchReached[i]);
                    if (branchReached[i]) {
                         nbBranchesCovered += 1;
                    }
               }
               writer.println("Number of Branches Covered: " + nbBranchesCovered);
               writer.println("Total Number of Branchs: " + branchReached.length);
               writer.println("Coverage: " + (float) nbBranchesCovered / branchReached.length);
               writer.close();
          } catch (IOException e) {
               e.printStackTrace();
          }

     }

}
