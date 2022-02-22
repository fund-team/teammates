package teammates.logic.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class FeedbackResponsesLogicBranchCoverage {

    // 56 branches
    public static Boolean[] branchesCovered = new Boolean[56];

    public static void changeBranchState(int numBranch) {
        branchesCovered[numBranch] = true;
    }

    public static void writeListBranchesCoveredInFile () {
        File file = new File("branchCoverage-FeedbackResponsesLogic-isResponseVisibleForUser.txt");
        int nbBranchesCovered = 0;
        try{
            file.createNewFile();
            PrintWriter writer = new PrintWriter(file);
            for (int i=0; i<branchesCovered.length; i++) {
                if (branchesCovered[i]!= null && branchesCovered[i]) {
                    writer.println(i);
                    nbBranchesCovered += 1;
                }
            }
            writer.println("Coverage: "+(float) nbBranchesCovered/branchesCovered.length);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
