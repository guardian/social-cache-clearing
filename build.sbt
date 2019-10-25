import com.gu.riffraff.artifact.RiffRaffArtifact.autoImport._

name := "social-cache-clearing"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-ssm" % "1.11.505",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-events" % "2.1.0",
  "com.amazonaws" % "amazon-kinesis-client" % "1.9.3",
  "com.twitter" %% "scrooge-core" % "4.18.0",
  "com.gu" %% "thrift-serializer" % "3.0.0",
  "com.gu" %% "content-api-client-default" % "15.4",
  "com.gu" %% "content-api-models-json" % "15.4",
  "org.scalaj" %% "scalaj-http" % "2.4.1"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffArtifactResources += (file("cfn.yaml"), "cfn/cfn.yaml")

assemblyMergeStrategy in assembly := {
  case PathList(ps @ _*) if ps.last endsWith ".thrift" => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}
