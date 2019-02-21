import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

name := "social-cache-clearing"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
  "com.amazonaws" % "amazon-kinesis-client" % "1.9.3",
  "com.twitter" %% "scrooge-core" % "4.18.0",
  "com.gu" %% "thrift-serializer" % "3.0.0",
  "com.gu" %% "content-api-models-scala" % "12.16" % "provided"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")
