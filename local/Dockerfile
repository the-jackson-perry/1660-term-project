FROM openjdk:8
RUN apt-get update && apt-get -y install libxext6 libxrender1 libxtst6
COPY . /usr/src/app
ENV GOOGLE_APPLICATION_CREDENTIALS="/usr/src/app/service-account-key.json"
WORKDIR /usr/src/app
RUN javac -cp ".:/usr/src/app/jar_files/*" FrontEndApp.java
CMD ["java", "-cp" , ".:/usr/src/app/jar_files/*", "FrontEndApp"]