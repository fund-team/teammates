package teammates.logic.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import teammates.common.datatransfer.AttributesDeletionQuery;
import teammates.common.datatransfer.CourseRoster;
import teammates.common.datatransfer.FeedbackParticipantType;
import teammates.common.datatransfer.attributes.FeedbackQuestionAttributes;
import teammates.common.datatransfer.attributes.FeedbackSessionAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.datatransfer.attributes.StudentAttributes;
import teammates.common.datatransfer.questions.FeedbackMcqQuestionDetails;
import teammates.common.datatransfer.questions.FeedbackMsqQuestionDetails;
import teammates.common.datatransfer.questions.FeedbackQuestionType;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Const;
import teammates.common.util.Logger;
import teammates.storage.api.FeedbackQuestionsDb;

/**
 * Handles operations related to feedback questions.
 *
 * @see FeedbackQuestionAttributes
 * @see FeedbackQuestionsDb
 */
public final class FeedbackQuestionsLogic {

    static final String USER_NAME_FOR_SELF = "Myself";

    private static final Logger log = Logger.getLogger();

    private static final FeedbackQuestionsLogic instance = new FeedbackQuestionsLogic();

    private final FeedbackQuestionsDb fqDb = FeedbackQuestionsDb.inst();

    private CoursesLogic coursesLogic;
    private FeedbackResponsesLogic frLogic;
    private FeedbackSessionsLogic fsLogic;
    private InstructorsLogic instructorsLogic;
    private StudentsLogic studentsLogic;

    private FeedbackQuestionsLogic() {
        // prevent initialization
    }

    public static FeedbackQuestionsLogic inst() {
        return instance;
    }

    void initLogicDependencies() {
        coursesLogic = CoursesLogic.inst();
        frLogic = FeedbackResponsesLogic.inst();
        fsLogic = FeedbackSessionsLogic.inst();
        instructorsLogic = InstructorsLogic.inst();
        studentsLogic = StudentsLogic.inst();
    }

    /**
     * Creates a new feedback question.
     *
     * @return the created question
     * @throws InvalidParametersException if the question is invalid
     */
    public FeedbackQuestionAttributes createFeedbackQuestion(FeedbackQuestionAttributes fqa)
            throws InvalidParametersException {

        List<FeedbackQuestionAttributes> questionsBefore = getFeedbackQuestionsForSession(fqa.getFeedbackSessionName(),
                fqa.getCourseId());

        FeedbackQuestionAttributes createdQuestion = fqDb.putEntity(fqa);

        adjustQuestionNumbers(questionsBefore.size() + 1, createdQuestion.getQuestionNumber(), questionsBefore);
        return createdQuestion;
    }

    /**
     * Gets a single question corresponding to the given parameters. <br>
     * <br>
     * <b>Note:</b><br>
     * * This method should only be used if the question already exists in the<br>
     * database and has an ID already generated.
     */
    public FeedbackQuestionAttributes getFeedbackQuestion(String feedbackQuestionId) {
        return fqDb.getFeedbackQuestion(feedbackQuestionId);
    }

    /**
     * Gets a single question corresponding to the given parameters.
     */
    public FeedbackQuestionAttributes getFeedbackQuestion(
            String feedbackSessionName,
            String courseId,
            int questionNumber) {
        return fqDb.getFeedbackQuestion(feedbackSessionName,
                courseId, questionNumber);
    }

    /**
     * Gets a {@link List} of every FeedbackQuestion in the given session.
     */
    public List<FeedbackQuestionAttributes> getFeedbackQuestionsForSession(
            String feedbackSessionName, String courseId) {

        List<FeedbackQuestionAttributes> questions = fqDb.getFeedbackQuestionsForSession(feedbackSessionName, courseId);
        questions.sort(null);

        // check whether the question numbers are consistent
        if (questions.size() > 1 && !areQuestionNumbersConsistent(questions)) {
            log.severe(courseId + ": " + feedbackSessionName + " has invalid question numbers");
        }

        return questions;
    }

    // TODO can be removed once we are sure that question numbers will be consistent
    private boolean areQuestionNumbersConsistent(List<FeedbackQuestionAttributes> questions) {
        Set<Integer> questionNumbersInSession = new HashSet<>();
        for (FeedbackQuestionAttributes question : questions) {
            if (!questionNumbersInSession.add(question.getQuestionNumber())) {
                return false;
            }
        }

        for (int i = 1; i <= questions.size(); i++) {
            if (!questionNumbersInSession.contains(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets a {@code List} of all questions for the given session that instructors
     * can view/submit.
     */
    public List<FeedbackQuestionAttributes> getFeedbackQuestionsForInstructors(
            String feedbackSessionName, String courseId, String userEmail) {
        List<FeedbackQuestionAttributes> questions = new ArrayList<>();

        questions.addAll(
                fqDb.getFeedbackQuestionsForGiverType(
                        feedbackSessionName, courseId, FeedbackParticipantType.INSTRUCTORS));

        if (userEmail != null && fsLogic.isCreatorOfSession(feedbackSessionName, courseId, userEmail)) {
            questions.addAll(
                    fqDb.getFeedbackQuestionsForGiverType(
                            feedbackSessionName, courseId, FeedbackParticipantType.SELF));
        }

        questions.sort(null);
        return questions;
    }

    /**
     * Filters through the given list of questions and returns a {@code List} of
     * questions that instructors can view/submit.
     */
    public List<FeedbackQuestionAttributes> getFeedbackQuestionsForInstructors(
            List<FeedbackQuestionAttributes> allQuestions, boolean isCreator) {

        List<FeedbackQuestionAttributes> questions = new ArrayList<>();

        for (FeedbackQuestionAttributes question : allQuestions) {
            if (question.getGiverType() == FeedbackParticipantType.INSTRUCTORS
                    || question.getGiverType() == FeedbackParticipantType.SELF && isCreator) {
                questions.add(question);
            }
        }

        return questions;
    }

    /**
     * Gets a {@code List} of all questions for the given session that students can
     * view/submit.
     */
    public List<FeedbackQuestionAttributes> getFeedbackQuestionsForStudents(
            String feedbackSessionName, String courseId) {
        List<FeedbackQuestionAttributes> questions = new ArrayList<>();

        questions.addAll(
                fqDb.getFeedbackQuestionsForGiverType(
                        feedbackSessionName, courseId, FeedbackParticipantType.STUDENTS));
        questions.addAll(
                fqDb.getFeedbackQuestionsForGiverType(
                        feedbackSessionName, courseId, FeedbackParticipantType.TEAMS));

        questions.sort(null);
        return questions;
    }

    /**
     * Filters through the given list of questions and returns a {@code List} of
     * questions that students can view/submit.
     */
    public List<FeedbackQuestionAttributes> getFeedbackQuestionsForStudents(
            List<FeedbackQuestionAttributes> allQuestions) {

        List<FeedbackQuestionAttributes> questions = new ArrayList<>();

        for (FeedbackQuestionAttributes question : allQuestions) {
            if (question.getGiverType() == FeedbackParticipantType.STUDENTS
                    || question.getGiverType() == FeedbackParticipantType.TEAMS) {
                questions.add(question);
            }
        }

        return questions;
    }

    /**
     * Returns true if a session has question in either STUDENTS type or TEAMS type.
     */
    public boolean sessionHasQuestions(String feedbackSessionName, String courseId) {
        return fqDb.hasFeedbackQuestionsForGiverType(feedbackSessionName, courseId, FeedbackParticipantType.STUDENTS)
                || fqDb.hasFeedbackQuestionsForGiverType(feedbackSessionName, courseId, FeedbackParticipantType.TEAMS);
    }

    /**
     * Returns true if a session has question in a specific giverType.
     */
    public boolean sessionHasQuestionsForGiverType(
            String feedbackSessionName, String courseId, FeedbackParticipantType giverType) {
        return fqDb.hasFeedbackQuestionsForGiverType(feedbackSessionName, courseId, giverType);
    }

    /**
     * Gets the email-name mapping of recipients for the given question for the
     * given giver.
     */
    Map<String, String> getRecipientsForQuestion(FeedbackQuestionAttributes question, String giver)
            throws EntityDoesNotExistException {

        InstructorAttributes instructorGiver = instructorsLogic.getInstructorForEmail(question.getCourseId(), giver);
        StudentAttributes studentGiver = studentsLogic.getStudentForEmail(question.getCourseId(), giver);

        Map<String, String> recipients = new HashMap<>();

        FeedbackParticipantType recipientType = question.getRecipientType();

        String giverTeam = getGiverTeam(giver, instructorGiver, studentGiver);

        String giverSection = getGiverSection(giver, instructorGiver, studentGiver);

        switch (recipientType) {
            case SELF:
                if (question.getGiverType() == FeedbackParticipantType.TEAMS) {
                    recipients.put(studentGiver.getTeam(), studentGiver.getTeam());
                } else {
                    recipients.put(giver, USER_NAME_FOR_SELF);
                }
                break;
            case STUDENTS:
                List<StudentAttributes> studentsInCourse = studentsLogic.getStudentsForCourse(question.getCourseId());
                for (StudentAttributes student : studentsInCourse) {
                    // Ensure student does not evaluate himself
                    if (!giver.equals(student.getEmail())) {
                        recipients.put(student.getEmail(), student.getName());
                    }
                }
                break;
            case STUDENTS_IN_SAME_SECTION:
                List<StudentAttributes> studentsInSection = studentsLogic.getStudentsForSection(giverSection,
                        question.getCourseId());
                for (StudentAttributes student : studentsInSection) {
                    // Ensure student does not evaluate himself
                    if (!giver.equals(student.getEmail())) {
                        recipients.put(student.getEmail(), student.getName());
                    }
                }
                break;
            case INSTRUCTORS:
                List<InstructorAttributes> instructorsInCourse = instructorsLogic
                        .getInstructorsForCourse(question.getCourseId());
                for (InstructorAttributes instr : instructorsInCourse) {
                    // Ensure instructor does not evaluate himself
                    if (!giver.equals(instr.getEmail())) {
                        recipients.put(instr.getEmail(), instr.getName());
                    }
                }
                break;
            case TEAMS:
                List<String> teams = coursesLogic.getTeamsForCourse(question.getCourseId());
                for (String team : teams) {
                    // Ensure student('s team) does not evaluate own team.
                    if (!giverTeam.equals(team)) {
                        // recipientEmail doubles as team name in this case.
                        recipients.put(team, team);
                    }
                }
                break;
            case TEAMS_IN_SAME_SECTION:
                List<String> teamsInSection = coursesLogic.getTeamsForSection(giverSection, question.getCourseId());
                for (String team : teamsInSection) {
                    // Ensure student('s team) does not evaluate own team.
                    if (!giverTeam.equals(team)) {
                        // recipientEmail doubles as team name in this case.
                        recipients.put(team, team);
                    }
                }
                break;
            case OWN_TEAM:
                recipients.put(giverTeam, giverTeam);
                break;
            case OWN_TEAM_MEMBERS:
                List<StudentAttributes> students = studentsLogic.getStudentsForTeam(giverTeam, question.getCourseId());
                for (StudentAttributes student : students) {
                    if (!student.getEmail().equals(giver)) {
                        recipients.put(student.getEmail(), student.getName());
                    }
                }
                break;
            case OWN_TEAM_MEMBERS_INCLUDING_SELF:
                List<StudentAttributes> teamMembers = studentsLogic.getStudentsForTeam(giverTeam,
                        question.getCourseId());
                for (StudentAttributes student : teamMembers) {
                    // accepts self feedback too
                    recipients.put(student.getEmail(), student.getName());
                }
                break;
            case NONE:
                recipients.put(Const.GENERAL_QUESTION, Const.GENERAL_QUESTION);
                break;
            default:
                break;
        }
        return recipients;
    }

    /**
     * Gets the recipients of a feedback question.
     *
     * @param question        the feedback question
     * @param instructorGiver can be null for student giver
     * @param studentGiver    can be null for instructor giver
     * @param courseRoster    if provided, the function can be completed without
     *                        touching database
     * @return a map which keys are the identifiers of the recipients and values are
     *         the names of the recipients
     */

    public Map<String, String> getRecipientsOfQuestion(
            FeedbackQuestionAttributes question,
            @Nullable InstructorAttributes instructorGiver, @Nullable StudentAttributes studentGiver,
            @Nullable CourseRoster courseRoster) {

        assert instructorGiver != null || studentGiver != null;
        // ID 0

        Map<String, String> recipients = new HashMap<>();

        boolean isStudentGiver = studentGiver != null;
        boolean isInstructorGiver = instructorGiver != null;

        String giverEmail = "";
        String giverTeam = "";
        String giverSection = "";
        if (isStudentGiver) {
            // ID 1
            giverEmail = studentGiver.getEmail();
            giverTeam = studentGiver.getTeam();
            giverSection = studentGiver.getSection();
        } else if (isInstructorGiver) {
            // ID 2
            giverEmail = instructorGiver.getEmail();
            giverTeam = Const.USER_TEAM_FOR_INSTRUCTOR;
            giverSection = Const.DEFAULT_SECTION;
        }
        // ID 3

        FeedbackParticipantType recipientType = question.getRecipientType();
        FeedbackParticipantType generateOptionsFor = recipientType;

        switch (recipientType) {
            case SELF:
                // ID 4
                if (question.getGiverType() == FeedbackParticipantType.TEAMS) {
                    // ID 5
                    recipients.put(giverTeam, giverTeam);
                } else {
                    // ID 6
                    recipients.put(giverEmail, USER_NAME_FOR_SELF);
                }
                // ID 7
                break;
            case STUDENTS:
                // ID 50
            case STUDENTS_IN_SAME_SECTION:
                // ID 8
                List<StudentAttributes> studentList;
                if (courseRoster == null) {
                    // ID 9
                    if (generateOptionsFor == FeedbackParticipantType.STUDENTS_IN_SAME_SECTION) {
                        // ID 10
                        studentList = studentsLogic.getStudentsForSection(giverSection, question.getCourseId());
                    } else {
                        // ID 11
                        studentList = studentsLogic.getStudentsForCourse(question.getCourseId());
                    }
                } else {
                    // ID 12
                    if (generateOptionsFor == FeedbackParticipantType.STUDENTS_IN_SAME_SECTION) {
                        // ID 13
                        final String finalGiverSection = giverSection;
                        studentList = courseRoster.getStudents().stream()
                                .filter(studentAttributes -> studentAttributes.getSection()
                                        .equals(finalGiverSection))
                                .collect(Collectors.toList());
                    } else {
                        // ID 14
                        studentList = courseRoster.getStudents();
                    }
                }
                for (StudentAttributes student : studentList) {
                    // ID 15
                    if (isInstructorGiver && !instructorGiver.isAllowedForPrivilege(
                            student.getSection(), question.getFeedbackSessionName(),
                            Const.InstructorPermissions.CAN_SUBMIT_SESSION_IN_SECTIONS)) {
                        // ID 16
                        // instructor can only see students in allowed sections for him/her
                        continue;
                    }
                    // Ensure student does not evaluate himself
                    if (!giverEmail.equals(student.getEmail())) {
                        // ID 17
                        recipients.put(student.getEmail(), student.getName());
                    }
                }
                // ID 18
                break;
            case INSTRUCTORS:
                // ID 19
                List<InstructorAttributes> instructorsInCourse;
                if (courseRoster == null) {
                    // ID 20
                    instructorsInCourse = instructorsLogic.getInstructorsForCourse(question.getCourseId());
                } else {
                    // ID 21
                    instructorsInCourse = courseRoster.getInstructors();
                }
                for (InstructorAttributes instr : instructorsInCourse) {
                    // ID 22
                    // remove hidden instructors for students
                    if (isStudentGiver && !instr.isDisplayedToStudents()) {
                        // ID 23
                        continue;
                    }
                    // Ensure instructor does not evaluate himself
                    if (!giverEmail.equals(instr.getEmail())) {
                        // ID 24
                        recipients.put(instr.getEmail(), instr.getName());
                    }
                }
                // ID 25
                break;
            case TEAMS:
                // ID 51
            case TEAMS_IN_SAME_SECTION:
                // ID 26
                Map<String, List<StudentAttributes>> teamToTeamMembersTable;
                List<StudentAttributes> teamStudents;
                if (generateOptionsFor == FeedbackParticipantType.TEAMS_IN_SAME_SECTION) {
                    // ID 27
                    teamStudents = studentsLogic.getStudentsForSection(giverSection, question.getCourseId());
                    teamToTeamMembersTable = CourseRoster.buildTeamToMembersTable(teamStudents);
                } else {
                    // ID 28
                    if (courseRoster == null) {
                        teamStudents = studentsLogic.getStudentsForCourse(question.getCourseId());
                        teamToTeamMembersTable = CourseRoster.buildTeamToMembersTable(teamStudents);
                    } else {
                        // ID 30
                        teamToTeamMembersTable = courseRoster.getTeamToMembersTable();
                    }
                }
                for (Map.Entry<String, List<StudentAttributes>> team : teamToTeamMembersTable.entrySet()) {
                    // ID 31
                    if (isInstructorGiver && !instructorGiver.isAllowedForPrivilege(
                            team.getValue().iterator().next().getSection(),
                            question.getFeedbackSessionName(),
                            Const.InstructorPermissions.CAN_SUBMIT_SESSION_IN_SECTIONS)) {
                        // ID 32
                        // instructor can only see teams in allowed sections for him/her
                        continue;
                    }
                    // Ensure student('s team) does not evaluate own team.
                    if (!giverTeam.equals(team.getKey())) {
                        // ID 33
                        // recipientEmail doubles as team name in this case.
                        recipients.put(team.getKey(), team.getKey());
                    }
                }
                // ID 34
                break;
            case OWN_TEAM:
                // ID 35
                recipients.put(giverTeam, giverTeam);
                break;
            case OWN_TEAM_MEMBERS:
                // ID 36
                List<StudentAttributes> students;
                if (courseRoster == null) {
                    // ID 37
                    students = studentsLogic.getStudentsForTeam(giverTeam, question.getCourseId());
                } else {
                    // ID 38
                    students = courseRoster.getTeamToMembersTable().getOrDefault(giverTeam, Collections.emptyList());
                }
                for (StudentAttributes student : students) {
                    // ID 39
                    if (!student.getEmail().equals(giverEmail)) {
                        // ID 40
                        recipients.put(student.getEmail(), student.getName());
                    }
                }
                // ID 41
                break;
            case OWN_TEAM_MEMBERS_INCLUDING_SELF:
                // ID 42
                List<StudentAttributes> teamMembers;
                if (courseRoster == null) {
                    // ID 43
                    teamMembers = studentsLogic.getStudentsForTeam(giverTeam, question.getCourseId());
                } else {
                    // ID 44
                    teamMembers = courseRoster.getTeamToMembersTable().getOrDefault(giverTeam, Collections.emptyList());
                }
                for (StudentAttributes student : teamMembers) {
                    // ID 45
                    // accepts self feedback too
                    recipients.put(student.getEmail(), student.getName());
                }
                // ID 46
                break;
            case NONE:
                // ID 47
                recipients.put(Const.GENERAL_QUESTION, Const.GENERAL_QUESTION);
                break;
            default:
                // ID 48
                break;
        }
        // ID 49

        return recipients;
    }

    /**
     * Builds a complete giver to recipient map for a {@code relatedQuestion}.
     *
     * @param relatedQuestion The question to be considered
     * @param courseRoster    the roster in the course
     * @return a map from giver to recipient for the question.
     */
    public Map<String, Set<String>> buildCompleteGiverRecipientMap(
            FeedbackQuestionAttributes relatedQuestion, CourseRoster courseRoster) {
        Map<String, Set<String>> completeGiverRecipientMap = new HashMap<>();

        List<String> possibleGivers = getPossibleGivers(relatedQuestion, courseRoster);
        for (String possibleGiver : possibleGivers) {
            switch (relatedQuestion.getGiverType()) {
                case STUDENTS:
                    StudentAttributes studentGiver = courseRoster.getStudentForEmail(possibleGiver);
                    completeGiverRecipientMap
                            .computeIfAbsent(possibleGiver, key -> new HashSet<>())
                            .addAll(getRecipientsOfQuestion(
                                    relatedQuestion, null, studentGiver, courseRoster).keySet());
                    break;
                case TEAMS:
                    StudentAttributes oneTeamMember = courseRoster.getTeamToMembersTable().get(possibleGiver).iterator()
                            .next();
                    completeGiverRecipientMap
                            .computeIfAbsent(possibleGiver, key -> new HashSet<>())
                            .addAll(getRecipientsOfQuestion(
                                    relatedQuestion, null, oneTeamMember, courseRoster).keySet());
                    break;
                case INSTRUCTORS:
                case SELF:
                    InstructorAttributes instructorGiver = courseRoster.getInstructorForEmail(possibleGiver);
                    completeGiverRecipientMap
                            .computeIfAbsent(possibleGiver, key -> new HashSet<>())
                            .addAll(getRecipientsOfQuestion(
                                    relatedQuestion, instructorGiver, null, courseRoster).keySet());
                    break;
                default:
                    log.severe("Invalid giver type specified");
                    break;
            }
        }

        return completeGiverRecipientMap;
    }

    /**
     * Gets possible giver identifiers for a feedback question.
     *
     * @param fqa          the feedback question
     * @param courseRoster roster of all students and instructors
     * @return a list of giver identifier
     */
    private List<String> getPossibleGivers(
            FeedbackQuestionAttributes fqa, CourseRoster courseRoster) {
        FeedbackParticipantType giverType = fqa.getGiverType();
        List<String> possibleGivers = new ArrayList<>();

        switch (giverType) {
            case STUDENTS:
                possibleGivers = courseRoster.getStudents()
                        .stream()
                        .map(StudentAttributes::getEmail)
                        .collect(Collectors.toList());
                break;
            case INSTRUCTORS:
                possibleGivers = courseRoster.getInstructors()
                        .stream()
                        .map(InstructorAttributes::getEmail)
                        .collect(Collectors.toList());
                break;
            case TEAMS:
                possibleGivers = new ArrayList<>(courseRoster.getTeamToMembersTable().keySet());
                break;
            case SELF:
                FeedbackSessionAttributes feedbackSession = fsLogic.getFeedbackSession(fqa.getFeedbackSessionName(),
                        fqa.getCourseId());
                possibleGivers = Collections.singletonList(feedbackSession.getCreatorEmail());
                break;
            default:
                log.severe("Invalid giver type specified");
                break;
        }

        return possibleGivers;
    }

    /**
     * Populates fields that need dynamic generation in a question.
     *
     * <p>
     * Currently, only MCQ/MSQ needs to generate choices dynamically.
     * </p>
     *
     * @param feedbackQuestionAttributes the question to populate
     * @param emailOfEntityDoingQuestion the email of the entity doing the question
     * @param teamOfEntityDoingQuestion  the team of the entity doing the question.
     *                                   If the entity is an instructor,
     *                                   it can be {@code null}.
     */
    public void populateFieldsToGenerateInQuestion(FeedbackQuestionAttributes feedbackQuestionAttributes,
            String emailOfEntityDoingQuestion, String teamOfEntityDoingQuestion) {
        CommonData.branchReached[0] = true;
        List<String> optionList;

        FeedbackParticipantType generateOptionsFor;

        if (feedbackQuestionAttributes.getQuestionType() == FeedbackQuestionType.MCQ) {
            CommonData.branchReached[1] = true;
            FeedbackMcqQuestionDetails feedbackMcqQuestionDetails = (FeedbackMcqQuestionDetails) feedbackQuestionAttributes
                    .getQuestionDetailsCopy();
            optionList = feedbackMcqQuestionDetails.getMcqChoices();
            generateOptionsFor = feedbackMcqQuestionDetails.getGenerateOptionsFor();
        } else if (feedbackQuestionAttributes.getQuestionType() == FeedbackQuestionType.MSQ) {
            CommonData.branchReached[2] = true;
            FeedbackMsqQuestionDetails feedbackMsqQuestionDetails = (FeedbackMsqQuestionDetails) feedbackQuestionAttributes
                    .getQuestionDetailsCopy();
            optionList = feedbackMsqQuestionDetails.getMsqChoices();
            generateOptionsFor = feedbackMsqQuestionDetails.getGenerateOptionsFor();
        } else {
            CommonData.branchReached[3] = true;
            // other question types
            return;
        }

        switch (generateOptionsFor) {
            case NONE:
                CommonData.branchReached[4] = true;
                break;
            case STUDENTS:
            case STUDENTS_IN_SAME_SECTION:
            case STUDENTS_EXCLUDING_SELF:
                CommonData.branchReached[5] = true;
                List<StudentAttributes> studentList;
                if (generateOptionsFor == FeedbackParticipantType.STUDENTS_IN_SAME_SECTION) {
                    CommonData.branchReached[6] = true;
                    String courseId = feedbackQuestionAttributes.getCourseId();
                    // Old line
                    /*
                     * StudentAttributes studentAttributes =
                     * studentsLogic.getStudentForEmail(emailOfEntityDoingQuestion,
                     * courseId);
                     */
                    StudentAttributes studentAttributes = studentsLogic.getStudentForEmail(courseId,
                            emailOfEntityDoingQuestion);
                    studentList = studentsLogic.getStudentsForSection(studentAttributes.getSection(), courseId);
                } else {
                    CommonData.branchReached[7] = true;
                    studentList = studentsLogic.getStudentsForCourse(feedbackQuestionAttributes.getCourseId());
                }

                if (generateOptionsFor == FeedbackParticipantType.STUDENTS_EXCLUDING_SELF) {
                    CommonData.branchReached[8] = true;
                    studentList.removeIf(studentInList -> studentInList.getEmail().equals(emailOfEntityDoingQuestion));
                }

                for (StudentAttributes student : studentList) {
                    CommonData.branchReached[9] = true;
                    optionList.add(student.getName() + " (" + student.getTeam() + ")");
                }

                optionList.sort(null);
                break;
            case TEAMS:
            case TEAMS_IN_SAME_SECTION:
            case TEAMS_EXCLUDING_SELF:
                CommonData.branchReached[10] = true;
                try {
                    List<String> teams;
                    if (generateOptionsFor == FeedbackParticipantType.TEAMS_IN_SAME_SECTION) {
                        CommonData.branchReached[11] = true;
                        String courseId = feedbackQuestionAttributes.getCourseId();
                        /*
                         * StudentAttributes studentAttributes = studentsLogic
                         * .getStudentForEmail(emailOfEntityDoingQuestion, courseId);
                         */
                        StudentAttributes studentAttributes = studentsLogic.getStudentForEmail(courseId,
                                emailOfEntityDoingQuestion);
                        teams = coursesLogic.getTeamsForSection(studentAttributes.getSection(), courseId);
                    } else {
                        CommonData.branchReached[12] = true;
                        teams = coursesLogic.getTeamsForCourse(feedbackQuestionAttributes.getCourseId());
                    }

                    if (generateOptionsFor == FeedbackParticipantType.TEAMS_EXCLUDING_SELF) {
                        CommonData.branchReached[13] = true;
                        teams.removeIf(team -> team.equals(teamOfEntityDoingQuestion));
                    }

                    for (String team : teams) {
                        CommonData.branchReached[14] = true;
                        optionList.add(team);
                    }

                    optionList.sort(null);
                } catch (EntityDoesNotExistException e) {
                    CommonData.branchReached[15] = true;
                    assert false : "Course disappeared";
                }
                break;
            case OWN_TEAM_MEMBERS_INCLUDING_SELF:
            case OWN_TEAM_MEMBERS:
                CommonData.branchReached[16] = true;
                if (teamOfEntityDoingQuestion != null) {
                    CommonData.branchReached[17] = true;
                    List<StudentAttributes> teamMembers = studentsLogic.getStudentsForTeam(teamOfEntityDoingQuestion,
                            feedbackQuestionAttributes.getCourseId());

                    if (generateOptionsFor == FeedbackParticipantType.OWN_TEAM_MEMBERS) {
                        CommonData.branchReached[18] = true;
                        teamMembers.removeIf(teamMember -> teamMember.getEmail().equals(emailOfEntityDoingQuestion));
                    }

                    teamMembers.forEach(teamMember -> optionList.add(teamMember.getName()));

                    optionList.sort(null);
                }
                break;
            case INSTRUCTORS:
                CommonData.branchReached[19] = true;
                List<InstructorAttributes> instructorList = instructorsLogic
                        .getInstructorsForCourse(feedbackQuestionAttributes.getCourseId());

                for (InstructorAttributes instructor : instructorList) {
                    CommonData.branchReached[20] = true;
                    optionList.add(instructor.getName());
                }

                optionList.sort(null);
                break;
            default:
                CommonData.branchReached[21] = true;
                assert false : "Trying to generate options for neither students, teams nor instructors";
                break;
        }

        if (feedbackQuestionAttributes.getQuestionType() == FeedbackQuestionType.MCQ) {
            CommonData.branchReached[22] = true;
            FeedbackMcqQuestionDetails feedbackMcqQuestionDetails = (FeedbackMcqQuestionDetails) feedbackQuestionAttributes
                    .getQuestionDetailsCopy();
            feedbackMcqQuestionDetails.setMcqChoices(optionList);
            feedbackQuestionAttributes.setQuestionDetails(feedbackMcqQuestionDetails);
        } else if (feedbackQuestionAttributes.getQuestionType() == FeedbackQuestionType.MSQ) {
            CommonData.branchReached[23] = true;
            FeedbackMsqQuestionDetails feedbackMsqQuestionDetails = (FeedbackMsqQuestionDetails) feedbackQuestionAttributes
                    .getQuestionDetailsCopy();
            feedbackMsqQuestionDetails.setMsqChoices(optionList);
            feedbackQuestionAttributes.setQuestionDetails(feedbackMsqQuestionDetails);
        }
        CommonData.printVisitedBranches();
    }

    private String getGiverSection(String defaultSection, InstructorAttributes instructorGiver,
            StudentAttributes studentGiver) {
        String giverSection = defaultSection;
        boolean isStudentGiver = studentGiver != null;
        boolean isInstructorGiver = instructorGiver != null;
        if (isStudentGiver) {
            giverSection = studentGiver.getSection();
        } else if (isInstructorGiver) {
            giverSection = Const.DEFAULT_SECTION;
        }
        return giverSection;
    }

    private String getGiverTeam(String defaultTeam, InstructorAttributes instructorGiver,
            StudentAttributes studentGiver) {
        String giverTeam = defaultTeam;
        boolean isStudentGiver = studentGiver != null;
        boolean isInstructorGiver = instructorGiver != null;
        if (isStudentGiver) {
            giverTeam = studentGiver.getTeam();
        } else if (isInstructorGiver) {
            giverTeam = Const.USER_TEAM_FOR_INSTRUCTOR;
        }
        return giverTeam;
    }

    /**
     * Returns true if the feedback question has been fully answered by the given
     * user.
     */
    public boolean isQuestionFullyAnsweredByUser(FeedbackQuestionAttributes question, String email)
            throws EntityDoesNotExistException {

        int numberOfResponsesGiven = frLogic.getFeedbackResponsesFromGiverForQuestion(question.getId(), email).size();
        int numberOfResponsesNeeded = question.getNumberOfEntitiesToGiveFeedbackTo();

        if (numberOfResponsesNeeded == Const.MAX_POSSIBLE_RECIPIENTS) {
            numberOfResponsesNeeded = getRecipientsForQuestion(question, email).size();
        }

        return numberOfResponsesGiven >= numberOfResponsesNeeded;
    }

    /**
     * Updates a feedback question by
     * {@code FeedbackQuestionAttributes.UpdateOptions}.
     *
     * <p>
     * Cascade adjust the question number of questions in the same session.
     *
     * <p>
     * Cascade adjust the existing response of the question.
     *
     * @return updated feedback question
     * @throws InvalidParametersException  if attributes to update are not valid
     * @throws EntityDoesNotExistException if the feedback question cannot be found
     */
    public FeedbackQuestionAttributes updateFeedbackQuestionCascade(
            FeedbackQuestionAttributes.UpdateOptions updateOptions)
            throws InvalidParametersException, EntityDoesNotExistException {
        FeedbackQuestionAttributes oldQuestion = fqDb.getFeedbackQuestion(updateOptions.getFeedbackQuestionId());
        if (oldQuestion == null) {
            throw new EntityDoesNotExistException("Trying to update a feedback question that does not exist.");
        }

        FeedbackQuestionAttributes newQuestion = oldQuestion.getCopy();
        newQuestion.update(updateOptions);
        int oldQuestionNumber = oldQuestion.getQuestionNumber();
        int newQuestionNumber = newQuestion.getQuestionNumber();

        List<FeedbackQuestionAttributes> previousQuestionsInSession = new ArrayList<>();
        if (oldQuestionNumber != newQuestionNumber) {
            // get questions in session before update
            String feedbackSessionName = oldQuestion.getFeedbackSessionName();
            String courseId = oldQuestion.getCourseId();
            previousQuestionsInSession = getFeedbackQuestionsForSession(feedbackSessionName, courseId);
        }

        // update question
        FeedbackQuestionAttributes updatedQuestion = fqDb.updateFeedbackQuestion(updateOptions);

        if (oldQuestionNumber != newQuestionNumber) {
            // shift other feedback questions (generate an empty "slot")
            adjustQuestionNumbers(oldQuestionNumber, newQuestionNumber, previousQuestionsInSession);
        }

        // adjust responses
        if (oldQuestion.areResponseDeletionsRequiredForChanges(updatedQuestion)) {
            frLogic.deleteFeedbackResponsesForQuestionCascade(oldQuestion.getId());
        }

        return updatedQuestion;
    }

    /**
     * Adjust questions between the old and new number,
     * if the new number is smaller, then shift up (increase qn#) all questions in
     * between.
     * if the new number is bigger, then shift down(decrease qn#) all questions in
     * between.
     */
    private void adjustQuestionNumbers(int oldQuestionNumber,
            int newQuestionNumber, List<FeedbackQuestionAttributes> questions) {
        try {
            if (oldQuestionNumber > newQuestionNumber && oldQuestionNumber >= 1) {
                for (int i = oldQuestionNumber - 1; i >= newQuestionNumber; i--) {
                    FeedbackQuestionAttributes question = questions.get(i - 1);
                    fqDb.updateFeedbackQuestion(
                            FeedbackQuestionAttributes.updateOptionsBuilder(question.getId())
                                    .withQuestionNumber(question.getQuestionNumber() + 1)
                                    .build());
                }
            } else if (oldQuestionNumber < newQuestionNumber && oldQuestionNumber < questions.size()) {
                for (int i = oldQuestionNumber + 1; i <= newQuestionNumber; i++) {
                    FeedbackQuestionAttributes question = questions.get(i - 1);
                    fqDb.updateFeedbackQuestion(
                            FeedbackQuestionAttributes.updateOptionsBuilder(question.getId())
                                    .withQuestionNumber(question.getQuestionNumber() - 1)
                                    .build());
                }
            }
        } catch (InvalidParametersException | EntityDoesNotExistException e) {
            assert false : "Adjusting question number should not cause: " + e.getMessage();
        }
    }

    /**
     * Deletes a feedback question cascade its responses and comments.
     *
     * <p>
     * Silently fail if question does not exist.
     */
    public void deleteFeedbackQuestionCascade(String feedbackQuestionId) {
        FeedbackQuestionAttributes questionToDelete = getFeedbackQuestion(feedbackQuestionId);

        if (questionToDelete == null) {
            return; // Silently fail if question does not exist.
        }

        // cascade delete responses for question.
        frLogic.deleteFeedbackResponsesForQuestionCascade(questionToDelete.getId());

        List<FeedbackQuestionAttributes> questionsToShiftQnNumber = getFeedbackQuestionsForSession(
                questionToDelete.getFeedbackSessionName(), questionToDelete.getCourseId());

        // delete question
        fqDb.deleteFeedbackQuestion(feedbackQuestionId);

        // adjust question numbers
        if (questionToDelete.getQuestionNumber() < questionsToShiftQnNumber.size()) {
            shiftQuestionNumbersDown(questionToDelete.getQuestionNumber(), questionsToShiftQnNumber);
        }
    }

    /**
     * Deletes questions using {@link AttributesDeletionQuery}.
     */
    public void deleteFeedbackQuestions(AttributesDeletionQuery query) {
        fqDb.deleteFeedbackQuestions(query);
    }

    // Shifts all question numbers after questionNumberToShiftFrom down by one.
    private void shiftQuestionNumbersDown(int questionNumberToShiftFrom,
            List<FeedbackQuestionAttributes> questionsToShift) {
        for (FeedbackQuestionAttributes question : questionsToShift) {
            if (question.getQuestionNumber() > questionNumberToShiftFrom) {
                try {
                    fqDb.updateFeedbackQuestion(
                            FeedbackQuestionAttributes.updateOptionsBuilder(question.getId())
                                    .withQuestionNumber(question.getQuestionNumber() - 1)
                                    .build());
                } catch (InvalidParametersException | EntityDoesNotExistException e) {
                    assert false : "Shifting question number should not cause: " + e.getMessage();
                }
            }
        }
    }

}
