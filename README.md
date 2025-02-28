# Real Estate Auth Microservice - Setup Guide

Welcome to the Real Estate Auth Microservice project. This guide provides step-by-step instructions to set up the development environment locally.

## About This Repository

The `real-estate-auth-microservice` repository serves as the Auth Service for the Real Estate project. The Auth Microservice handles all authentication-related operations such as user login, token generation, and validation. It is built using Kotlin and SpringBoot.

## Prerequisites

- Docker: Ensure Docker is installed and running on your machine. Download from [docker.com](https://www.docker.com) if needed.
- Access to a terminal (bash, cmd, PowerShell, etc.).
- Internet connection to pull necessary images.
- Keycloak: Ensure Keycloak is running locally. The guide for configuring Keycloak locally can be found [here](https://github.com/nullius-software/real-estate-keycloak-render).
- Discovery Service: Ensure the discovery service is running locally. The guide for setting up the discovery service can be found [here](https://github.com/nullius-software/real-estate-discovery-service).

## Step 1: Clone the Repository

First, clone the `real-estate-auth-microservice` repository to your local machine:

```bash
git clone https://github.com/nullius-software/real-estate-auth-microservice.git
cd real-estate-auth-microservice
```

## Step 2: Build the Docker Image

Build the Docker image for the Auth Microservice:

```bash
docker build -t real-estate-auth-microservice .
```

## Step 3: Run the Docker Container

Run the Docker container for the Auth Microservice:

```bash
docker run -p 8083:8083 real-estate-auth-microservice
```

### Explanation:

- `-p 8083:8083`: Maps port 8083 from the container to your local machine.

Wait for the container to start. You’ll see logs indicating the Auth Microservice is ready when something like this appears:

```
Started AuthMicroserviceApplication in X.XXX seconds
```

## Step 4: Access the Auth Microservice

Open your browser and go to: [http://localhost:8083](http://localhost:8083).

You should see the Auth Microservice indicating that it is running.

## Step 5: Verify the Configuration

Ensure that the other microservices in the Real Estate project are configured to communicate with the Auth Microservice by checking their configuration files. They should have the Auth Microservice URL set to `http://localhost:8083`.
