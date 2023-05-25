//import java.io.File
//import java.nio.file.FileVisitResult
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.SimpleFileVisitor
//import java.nio.file.attribute.BasicFileAttributes
//
//fun deleteOnExit(file: File) = deleteOnExit(file.toPath())
//fun deleteOnExit(directoryToBeDeleted: Path) = Runtime.getRuntime().addShutdownHook(Thread {
//    tryOrNull {
//        Files.walkFileTree(directoryToBeDeleted, object : SimpleFileVisitor<Path>() {
//            override fun visitFile(
//                file: Path,
//                @SuppressWarnings("unused") attrs: BasicFileAttributes
//            ): FileVisitResult {
//                file.toFile().deleteOnExit()
//                return FileVisitResult.CONTINUE
//            }
//
//            override fun preVisitDirectory(
//                dir: Path,
//                @SuppressWarnings("unused") attrs: BasicFileAttributes
//            ): FileVisitResult {
//                dir.toFile().deleteOnExit()
//                return FileVisitResult.CONTINUE
//            }
//        })
//    }
//})
//
//
