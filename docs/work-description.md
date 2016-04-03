
Our entire pipeline works as follows.

1. Constructing dictionary and document record ["dictionary.txt" and "documentRecord.dat"]

  * Run the "BuildTopicModel.java"
  * Dictionary and document record is created using dataset located at "./data/bbc/"
  * In this phase, "dictionaryWithFrequency.txt" file is also generated

2. Run topic model using LDA-C

  * Double click the "run.bat" file, topic model will be generated and stored in "./topic_model/" folder
  * Command used to generate topic model is "lda-win64 est 0.6 5 settings.txt documentRecord.dat seeded topic_model"
  * Third parameter value ("5") in the command represents "number of topics"
  * "settings.txt" file contains all required parameter values

3. Generate "query.dat" file

 * Don't need to run any specific java file
 * When you run "Evaluate.java" or "Runner.java", it calls "initializeGeneration" function from "QueryTopicInference.java" for every user
 * "query.dat" file contains information about all the queries of a particular user

4. Generate topic specific words and store them in "./topic_repo/"

 * Run the "GenerateTopicWords.java"
 * Each topic repo (say, "./topic_repo/topic 0.txt") contains all the respective topic specific words and their likelihood

5. Generate user profile for top k users

 * Run the "ProfileBuilder.java"
 * User profile will be generated for training and testing 
 * User profiles are stored at "./data/user_profiles/"

6. Finally either run "Evaluate.java" or "Runner.java"

 * "Evaluate.java" - run to evaluate our entire pipeline
 * "Runner.java" - run for interactive searching
