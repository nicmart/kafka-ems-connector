/*
 * Copyright 2017-2022 Celonis Ltd
 */
package com.celonis.kafka.connect.ems.config

import com.celonis.kafka.connect.ems.config.EmsSinkConfigConstants.ERROR_POLICY_DOC
import com.celonis.kafka.connect.ems.config.EmsSinkConfigConstants.ERROR_POLICY_KEY
import com.celonis.kafka.connect.ems.errors.ErrorPolicy
import com.celonis.kafka.connect.ems.errors.ErrorPolicy.Continue
import com.celonis.kafka.connect.ems.errors.ErrorPolicy.Retry
import com.celonis.kafka.connect.ems.errors.ErrorPolicy.Throw
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ErrorPolicyTests extends AnyFunSuite with Matchers {
  test(s"return an error if $ERROR_POLICY_KEY is missing") {
    val expectedMessage = s"Invalid [$ERROR_POLICY_KEY]. $ERROR_POLICY_DOC"
    ErrorPolicy.extract(Map.empty) shouldBe Left(expectedMessage)
    ErrorPolicy.extract(Map("a" -> "b", "b" -> 1)) shouldBe Left(expectedMessage)
    ErrorPolicy.extract(Map("a" -> "b", ERROR_POLICY_KEY + ".ext" -> 1)) shouldBe Left(expectedMessage)
  }

  test(s"return an error if $ERROR_POLICY_KEY is empty") {
    val expectedMessage = s"Invalid [$ERROR_POLICY_KEY]. $ERROR_POLICY_DOC"
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "")) shouldBe Left(expectedMessage)
  }

  test(s"return an error if $ERROR_POLICY_KEY is not a string") {
    val expectedMessage = s"Invalid [$ERROR_POLICY_KEY]. $ERROR_POLICY_DOC"
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> 2)) shouldBe Left(expectedMessage)
  }

  test(s"return an error if $ERROR_POLICY_KEY is invalid") {
    val expectedMessage = s"Invalid [$ERROR_POLICY_KEY]. $ERROR_POLICY_DOC"
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "retry.")) shouldBe Left(expectedMessage)
  }

  test(s"return the target table provided with $ERROR_POLICY_KEY") {
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "retry")) shouldBe Right(Retry)
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "throw")) shouldBe Right(Throw)
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "continue")) shouldBe Right(Continue)

    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "rEtry")) shouldBe Right(Retry)
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "THROW")) shouldBe Right(Throw)
    ErrorPolicy.extract(Map(ERROR_POLICY_KEY -> "conTinue")) shouldBe Right(Continue)
  }
}
