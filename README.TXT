
1) Run Weka:
   java -Xmx1024m -cp ./ weka.gui.experiment.Experimenter

2) To load sample experiment files, click on Open, select one of the following:
   experiments/restaurant.exp
   experiments/cora-ATVYOall.exp

3) Switch to Run tab, click "Start"

4) When the experiment is done, switch to Analyze tab, click on "Experiment"
   (top right), then on "Perform test" (middle left). Top line is recall, under
   it are precision values.
   

To load your own data, click "Add new..." in Setup tab, and add your own
dataset.

To experiment with different classifiers and string metrics, click on the
current system description between "Choose" and "Add" buttons (mid-right).
Click left of "Choose" next to "metric".  The combining classifier is
controlled by the classifier settings, the bottom-level string metric(s) are
at the bottom under "string metrics". 