build:
	mvn clean package -DskipTests

test:
	mvn clean test

dist:
	python3 dist/build_portable.py
