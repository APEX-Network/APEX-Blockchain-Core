package com.apex.core

trait NodeViewComponent {
  self =>

  type NVCT >: self.type <: NodeViewComponent
}
