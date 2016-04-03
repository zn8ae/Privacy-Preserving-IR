
## Generating Topic Model

Requirement: [BBC dataset](http://mlg.ucd.ie/datasets/bbc.html), [Binary for LDA-C](https://github.com/magsilva/lda-c/tree/master/bin), [Settings file](https://github.com/wasiuva/Privacy-Preserving-IR/blob/master/settings.txt) to set parameters for LDA

### Step. 1 - Constructing dictionary and document record
  * Run [BuildTopicModel.java](https://github.com/wasiuva/Privacy-Preserving-IR/blob/master/src/edu/virginia/cs/model/BuildTopicModel.java)
  * Dictionary and document record will be created using BBC dataset
    + BBC dataset should be located at - **project_root_directory]/data/bbc/**
    + Dictionary ("dictionary.txt") and document record ("documentRecord.dat") will be placed at the project root directory
  * This step also generated "dictionaryWithFrequency.txt" file and placed at the project root directory

**File description**:
  * dictionary.txt: This file contains unigrams and bigrams found in the BBC dataset. Each line contains one unigram/bigram.
  * documentRecord.dat: This file contains one line per BBC document. Each line looks like the following.
  <pre>
   350 501:1 530:1 723:1 443:1 598:1 621:1 707:1 561:1 591:1 490:1 483:1 487:1 438:1 688:1 573:1 604:1 471:2
   31:1 627:1 682:1 477:4 603:1 607:1 562:3 698:1 474:1 544:1 656:1 472:1 457:1 513:1 413:1 410:1 3:1 
   632:1 569:1 488:1 499:1 599:1 439:1 401:7 595:2 713:1 526:1 648:1 179:1 626:1 518:3 655:1 524:1 624:1 
  </pre>
  The first numeric value represents the total number of unique terms found in the document. Then all <code>x:y</code> value represents <code>term index in the dictionary:term frequency</code>. All the values are separated by space.
  * dictionaryWithFrequency.txt: This file contains unigrams and bigrams with their total term frequency over the entire BBC dataset.     Each line contains one unigram/bigram and corresponding total term frequency seperated by space.

### Step. 2 - Generate the topic model using LDA-C

  * Double click the [run.bat](https://github.com/wasiuva/Privacy-Preserving-IR/blob/master/run-lda.bat) file (for windows environment), topic model will be generated and stored in **project_root_directory]/topic_model/** folder
  * Command written in [run.bat](https://github.com/wasiuva/Privacy-Preserving-IR/blob/master/run-lda.bat) file is **lda-win64 est 0.6 5 settings.txt documentRecord.dat seeded topic_model**
  * Third parameter value ("5") in the command represents "number of topics" for the topic model
  * [settings.txt](https://github.com/wasiuva/Privacy-Preserving-IR/blob/master/settings.txt) file should contain all required parameter values

## Generating Topic Model

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
