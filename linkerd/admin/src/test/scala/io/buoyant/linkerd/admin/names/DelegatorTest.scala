package io.buoyant.linkerd.admin.names

import com.twitter.finagle.{Status => SvcStatus, _}
import com.twitter.util._
import io.buoyant.test.Awaits
import java.net.InetSocketAddress
import org.scalatest.FunSuite

class DelegatorTest extends FunSuite with Awaits {

  val dtab = Dtab.read("""
    /bah/humbug => /$/inet/127.1/8080 ;
    /foo => /bah | /$/fail ;
    /foo => /bar ;
    /boo => /foo ;
    /meh => /heh ;
  """)

  test("explain neg delegation") {
    val path = Path.Utf8("nope")
    assert(await(Delegator(dtab, path).values.toFuture()) ==
      Return(DelegateTree.Neg(path, Dentry.nop)))
  }

  test("explain delegate delegation") {
    val path = Path.read("/meh/hey")
    assert(await(Delegator(dtab, path).values.toFuture()) ==
      Return(DelegateTree.Delegate(path, Dentry.nop, DelegateTree.Neg(Path.read("/heh/hey"), Dentry.read("/meh=>/heh")))))
  }

  test("explain alt delegation") {
    val path = Path.read("/boo/lol")
    assert(await(Delegator(dtab, path).values.toFuture()) ==
      Return(DelegateTree.Delegate(path, Dentry.nop, DelegateTree.Alt(
        Path.read("/foo/lol"),
        Dentry.read("/boo=>/foo"),
        DelegateTree.Neg(Path.read("/bar/lol"), Dentry.read("/foo=>/bar")),
        DelegateTree.Neg(Path.read("/bah/lol"), Dentry.read("/foo=>/bah|/$/fail")),
        DelegateTree.Fail(Path.read("/$/fail/lol"), Dentry.read("/foo=>/bah|/$/fail"))
      ))))
  }

  test("explain bound delegation") {
    val path = Path.read("/boo/humbug/ya")
    assert(await(Delegator(dtab, path).values.toFuture()) ==
      Return(DelegateTree.Delegate(path, Dentry.nop, DelegateTree.Alt(
        Path.read("/foo/humbug/ya"),
        Dentry.read("/boo=>/foo"),
        DelegateTree.Neg(Path.read("/bar/humbug/ya"), Dentry.read("/foo=>/bar")),
        DelegateTree.Delegate(Path.read("/bah/humbug/ya"), Dentry.read("/foo=>/bah | /$/fail"), DelegateTree.Leaf(
          Path.read("/$/inet/127.1/8080/ya"),
          Dentry.read("/bah/humbug=>/$/inet/127.1/8080"),
          Name.Bound(Var.value(Addr.Pending), Path.read("/$/inet/127.1/8080"), Path.Utf8("ya"))
        )),
        DelegateTree.Fail(Path.read("/$/fail/humbug/ya"), Dentry.read("/foo=>/bah | /$/fail"))
      ))))
  }
}