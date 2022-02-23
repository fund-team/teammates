package teammates.logic.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class GetRecipientsForQuestionsBranchCoverage {

    // 56 branches
    public static boolean[] branchesCovered = new boolean[24];

    public static void changeBranchState(int numBranch) {
        branchesCovered[numBranch] = true;
    }

    public static void writeListBranchesCoveredInFile () {
        File file = new File("branchCoverage-FeedbackQuestionsLogic-getRecipientsForQuestionsBranchCoverage.txt");
        int numBranchesCovered = 0;
        try{
            file.createNewFile();
            PrintWriter writer = new PrintWriter(file);
            for (int i=0; i<branchesCovered.length; i++) {
                if (branchesCovered[i]) {
                    numBranchesCovered += 1;
                }
                writer.println("Reached Branch " + i + ": " + branchesCovered[i]);
            }
            writer.println("Number of branches covered: " + numBranchesCovered);
            writer.println("Total number of branches: " + branchesCovered.length);
            writer.println("Coverage: "+(float) numBranchesCovered/branchesCovered.length);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
