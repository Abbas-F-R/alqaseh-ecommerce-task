# Builds and runs the API without a local .NET SDK:  docker compose up --build
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY AlQaseh_Ecommerce_API.csproj .
RUN dotnet restore
COPY . .
RUN dotnet publish AlQaseh_Ecommerce_API.csproj -c Release -o /app --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app .
EXPOSE 8080
ENTRYPOINT ["dotnet", "AlQaseh_Ecommerce_API.dll"]
