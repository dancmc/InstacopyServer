import org.neo4j.driver.v1.AuthTokens
import org.neo4j.driver.v1.GraphDatabase
import org.neo4j.driver.v1.Values
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

class MachineLearning {

    companion object {
        fun run(){
            val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "personal1"));

            val list = ArrayList<Pair<String,String>>()
            var b = 0

            driver.session().use { session ->
                println("here")
                try {
                    session.writeTransaction {

                        it.run("CREATE CONSTRAINT ON (node:Node)\n" +
                                "ASSERT node.id IS UNIQUE")


                    }
                }catch (e:Exception){
                    println(e.message)
                }
                println("here")

                BufferedReader(FileReader(File("/users/daniel/downloads/graph-predict/approved_edges_500_15_3.txt"))).use { br ->
                    var line = br.readLine()

                    while (line != null) {
                        val nodes = line.split("\t")
                        val node1 = nodes[0]
                        val node2 = nodes[1]

                        list.add(Pair(node1, node2))
                        if (list.size > 6000) {
                            session.writeTransaction {
                                list.forEach { listnode ->

                                    it.run("MATCH (node1:Node{id:{id1}}) MATCH (node2:Node{id:{id2}}) MERGE (node1)-[:FOLLOW]->(node2)",
                                            Values.parameters("id1", listnode.first, "id2", listnode.second))
                                }
                            }
                            list.clear()
                        }
                        b++
                        if(b%100==0){
                            println(b)
                        }
                        line = br.readLine()
                    }
                }
                session.writeTransaction {
                    list.forEach { listnode ->

                        it.run("MATCH (node1:Node{id:{id1}}) MATCH (node2:Node{id:{id2}}) MERGE (node1)-[:FOLLOW]->(node2)",
                                Values.parameters("id1", listnode.first, "id2", listnode.second))
                    }
                }

            }

        }
    }
}