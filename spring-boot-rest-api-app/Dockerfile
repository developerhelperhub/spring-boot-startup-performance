FROM amd64/alpine

#Set the working directory
WORKDIR /app

#Copy the built application from the first stage
COPY target/app-native-binary /app/app-native-binary

#Expose port which our spring boot application is running
EXPOSE 8080 

#Command to run the application
ENTRYPOINT ["/app/app-native-binary"]