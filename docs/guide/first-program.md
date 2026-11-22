# Write your first Protos program

Build an order summary while learning the pieces you will use in most Protos
programs: bindings, functions, collections, decisions, and objects.

First [install Protos](00-try-protos.md) and confirm that `protos --version`
works. The commands below assume `protos` is on your `PATH`. From a source
checkout with a prepared runtime, you can use `bin/protos` instead.

## 1. Create and run a file

Create a file named `order.protos` in your working directory:

```protos
print("Order summary")
```

Run it from that directory:

```sh
protos order.protos
```

Expected output:

```text
Order summary
```

The standalone CLI supplies `print`. You can pass a String or a number to it;
each call writes one line. An imported file does not automatically receive
the entry program's `print` binding. Pass output explicitly when splitting
a program into modules.

## 2. Name values and update them

Replace the file with:

```protos
quantity: 3
unitPrice: 12
subtotal: quantity * unitPrice

print("Subtotal")
print(subtotal)

quantity = 4
print("Updated quantity")
print(quantity)
```

Run the same command again. Expected output:

```text
Subtotal
36
Updated quantity
4
```

Use `:` to create a named slot and `=` to change an existing one. A binding
stores a value: changing `quantity` does not recalculate the value already
stored in `subtotal`.

**Try it:** add `subtotal = quantity * unitPrice` after changing `quantity`,
then print `subtotal`. It should now be `48`.

For more detail, read [values and bindings](01-bindings-contexts-and-state.md).

## 3. Put a calculation in a function

A function is a Closure: a value containing code that you can call. Replace
the file with:

```protos
lineTotal: (quantity, unitPrice) => {
    quantity * unitPrice
}

print(lineTotal(3, 12))
print(lineTotal(2, 7))
```

Expected output:

```text
36
14
```

The names between parentheses are parameters. Each call supplies their values.
The last expression in the body is the call's normal result; no explicit
`return` is needed here.

**Try it:** add a third call for five items priced at four each. Expect `20`.

Continue with [functions and methods](03-closures-methods-and-receivers.md)
when you want defaults, named arguments, or captured state.

## 4. Add several lines and make a decision

Replace the file with this complete program:

```protos
lineTotal: (quantity, unitPrice) => {
    quantity * unitPrice
}

lines: [lineTotal(3, 12), lineTotal(2, 7)]
total: 0

for(amount in lines) {
    total = total + amount
}

print("Order total")
print(total)

if(total >= 50) {
    print("Free delivery")
} else {
    print("Delivery charge applies")
}
```

Expected output:

```text
Order total
50
Free delivery
```

`[a, b]` creates an Array. The loop visits each amount, and its body updates
the existing outer `total` slot. `total >= 50` produces the Boolean that
chooses the `if` branch. Keep `} else {` together as shown.

**Try it:** add `lineTotal(1, 5)` to `lines` and expect `55`. Then change
the free-delivery threshold to `60` and expect the other message.

Read [decisions and loops](04-control-flow-through-protocols.md) for numeric
ranges and `while`, or [collections](05-values-identity-equality-and-collections.md)
for indexing, Maps, and transformations.

## 5. Give each line named fields

Amounts alone are enough to total an order, but named fields make it easier to
keep related data together. Replace the file with:

```protos
line: {
    description: "Notebook"
    quantity: 3
    unitPrice: 12
}

print(line.description)
print(line.quantity * line.unitPrice)

line.quantity = 4
print(line.quantity * line.unitPrice)
```

Expected output:

```text
Notebook
36
48
```

The braced value is an object with three slots. Read a slot with `object.name`;
change an existing writable slot with `object.name = value`.

**Try it:** make an Array containing two line objects. Adapt the previous
loop to calculate `line.quantity * line.unitPrice` for each object. With
quantities/prices `3`/`12` and `2`/`7`, expect a total of `50`.

The [object chapter](02-objects-delegation-and-composition.md) continues with
methods, delegation, and reuse.

## Where to go next

You can now run a file, name and change values, call a function, traverse an
Array, choose a branch, and group data in an object.

- Follow [the language lessons](README.md#learn-the-language) to understand
  lookup, methods, and collections in more depth.
- [Split the program into files](06-modules-and-imports.md) when it grows.
  Start with SOURCE imports and passing CLI output explicitly.
- [Run executable tutorials](../../protos/tutorials/README.md) for smaller
  exercises on each concept.
- [Choose a cookbook example](../../protos/examples/README.md) when you have
  a specific task to solve.

[Back to the guide](README.md)
