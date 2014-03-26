# name of the file that will contain all source files
SOURCE_FILE_LIST = sourcefiles.log

# name of the file that will contain all class file names
CLASS_FILE_LIST = classfiles.log

# name of the file where the compilation log will be stored
COMPILE_LOG_FILE = compile.log

# person to receive the email
CLASSPATH = ./:../:../../:../../../:/u/ml/packages/:/u/ml/packages/Jama-1.0.1.jar:/u/ml/packages/cplex.jar:/u/ml/packages/jmage.jar:/u/ml/packages/ir.jar:/u/ml/packages/junit/junit.jar

# Compile all the source files in the current location
compile:
	find ./ -path './*.java' > ${SOURCE_FILE_LIST} ;\
	javac -O -classpath ${CLASSPATH} @${SOURCE_FILE_LIST} ;\


