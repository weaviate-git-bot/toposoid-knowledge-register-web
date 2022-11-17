/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.{FeatureVectorId, FeatureVectorSearchResult}
import org.neo4j.driver.{Record, Result}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, call, status, _}
import play.api.test.{FakeRequest, _}

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 *
 * For more information, see https://www.playframework.com/documentation/latest/ScalaTestingWithScalaTest
 */
class HomeControllerSpecEnglish extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerTest with Injecting {


  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
  }
  private def existFeatureVector(propositionId:String, lang:String, sentenceId:String):Boolean ={
    val json: String = Json.toJson(FeatureVectorId(id = propositionId + "#" + lang + "-s" + sentenceId )).toString()
    val featureVectorSearchResultJson = ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_VALD_ACCESSOR_HOST"), "9010", "searchById")
    val featureVectorSearchResult: FeatureVectorSearchResult = Json.parse(featureVectorSearchResultJson).as[FeatureVectorSearchResult]
    featureVectorSearchResult.ids.size == 1
  }

  private def deleteFeatureVector(propositionId:String, lang:String, sentenceId:String):Unit = {
    val json: String = Json.toJson(FeatureVectorId(id = propositionId + "#" + lang + "-s" + sentenceId )).toString()
    ToposoidUtils.callComponent(json, conf.getString("TOPOSOID_VALD_ACCESSOR_HOST"), "9010", "searchById")
  }

  private def getPropositionId(queryResult:Result):String = {
    if(queryResult.hasNext()){
      val record:Record = queryResult.next()
      return record.get("x").asPath().nodes.iterator().next().get("propositionId").asString()
    }
    ""
  }

  "HomeController POST(english knowledge)" should {
    "returns an appropriate response" in {
      val controller: HomeController = inject[HomeController]
      val jsonStr: String = """{"knowledgeList":[{"sentence":"This is a Test.", "lang": "en_US", "extentInfoJson":"{}", "isNegativeSentence":false}]}"""
      val fr = FakeRequest(POST, "/regist")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(jsonStr))
      val result = call(controller.regist(), fr)
      status(result) mustBe OK
      Thread.sleep(60000)
      val query = "MATCH x = (:ClaimNode{surface:'This'})-[:ClaimEdge]->(:ClaimNode{surface:'is'})<-[:ClaimEdge]-(:ClaimNode{surface:'Test'})<-[:ClaimEdge]-(:ClaimNode{surface:'a'})　return x"
      val queryResult: Result = Neo4JAccessor.executeQueryAndReturn(query)
      assert(queryResult.hasNext())
      val propositionId:String = getPropositionId(queryResult)
      assert(existFeatureVector(propositionId, "en_US", "0"))
      deleteFeatureVector(propositionId, "en_US", "0")

    }
  }

  "HomeController POST(english KnowledgeSentenceSet)" should {
    "returns an appropriate response" in {
      val controller: HomeController = inject[HomeController]
      val jsonStr: String =
        """{
          |	"premiseList": [
          |		{
          |			"sentence": "This is premise-1.",
          |			"lang": "en_US",
          |			"extentInfoJson": "{}",
          |     "isNegativeSentence":false
          |		},
          |		{
          |			"sentence": "This is premise-2.",
          |			"lang": "en_US",
          |			"extentInfoJson": "{}",
          |     "isNegativeSentence":false
          |		}
          |	],
          |	"premiseLogicRelation": [
          |		{
          |			"operator": "AND",
          |			"sourceIndex": 0,
          |			"destinationIndex": 1
          |		}
          |	],
          |	"claimList": [
          |		{
          |			"sentence": "This is claim-1.",
          |			"lang": "en_US",
          |			"extentInfoJson": "{}",
          |     "isNegativeSentence":false
          |		},
          |		{
          |			"sentence": "This is claim-2.",
          |			"lang": "en_US",
          |			"extentInfoJson": "{}",
          |     "isNegativeSentence":false
          |		}
          |	],
          |	"claimLogicRelation": [
          |		{
          |			"operator": "OR",
          |			"sourceIndex": 0,
          |			"destinationIndex": 1
          |		}
          |	]
          |}""".stripMargin
      val fr = FakeRequest(POST, "/registByKnowledgeSentenceSet")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(jsonStr))
      val result = call(controller.registByKnowledgeSentenceSet(), fr)
      status(result) mustBe OK

      Thread.sleep(60000)
      val query = "MATCH x=(:ClaimNode{surface:'claim-1'})-[:ClaimEdge]-(:ClaimNode)-[:LogicEdge{operator:'OR'}]-(:ClaimNode)-[:ClaimEdge]-(:ClaimNode{surface:'claim-2'}) return x"
      val queryResult:Result = Neo4JAccessor.executeQueryAndReturn(query)
      assert(queryResult.hasNext())
      val query2 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:PremiseEdge]-(:PremiseNode)-[:LogicEdge{operator:'AND'}]-(:PremiseNode)-[:PremiseEdge]-(:PremiseNode{surface:'premise-2'}) return x"
      val queryResult2:Result = Neo4JAccessor.executeQueryAndReturn(query2)
      assert(queryResult2.hasNext())
      val query3 = "MATCH x=(:PremiseNode{surface:'premise-1'})-[:PremiseEdge]-(:PremiseNode)-[:LogicEdge{operator:'IMP'}]-(:ClaimNode)-[:ClaimEdge]-(:ClaimNode{surface:'claim-1'}) return x"
      val queryResult3:Result = Neo4JAccessor.executeQueryAndReturn(query3)
      assert(queryResult3.hasNext())
      val propositionId:String = getPropositionId(queryResult)
      for( sentenceId <- 0 until 4){
        assert(existFeatureVector(propositionId, "en_US", sentenceId.toString))
        deleteFeatureVector(propositionId, "en_US", sentenceId.toString)
      }

    }

  }
}