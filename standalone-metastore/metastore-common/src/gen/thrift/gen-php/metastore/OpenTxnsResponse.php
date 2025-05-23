<?php
namespace metastore;

/**
 * Autogenerated by Thrift Compiler (0.16.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
use Thrift\Base\TBase;
use Thrift\Type\TType;
use Thrift\Type\TMessageType;
use Thrift\Exception\TException;
use Thrift\Exception\TProtocolException;
use Thrift\Protocol\TProtocol;
use Thrift\Protocol\TBinaryProtocolAccelerated;
use Thrift\Exception\TApplicationException;

class OpenTxnsResponse
{
    static public $isValidate = false;

    static public $_TSPEC = array(
        1 => array(
            'var' => 'txn_ids',
            'isRequired' => true,
            'type' => TType::LST,
            'etype' => TType::I64,
            'elem' => array(
                'type' => TType::I64,
                ),
        ),
    );

    /**
     * @var int[]
     */
    public $txn_ids = null;

    public function __construct($vals = null)
    {
        if (is_array($vals)) {
            if (isset($vals['txn_ids'])) {
                $this->txn_ids = $vals['txn_ids'];
            }
        }
    }

    public function getName()
    {
        return 'OpenTxnsResponse';
    }


    public function read($input)
    {
        $xfer = 0;
        $fname = null;
        $ftype = 0;
        $fid = 0;
        $xfer += $input->readStructBegin($fname);
        while (true) {
            $xfer += $input->readFieldBegin($fname, $ftype, $fid);
            if ($ftype == TType::STOP) {
                break;
            }
            switch ($fid) {
                case 1:
                    if ($ftype == TType::LST) {
                        $this->txn_ids = array();
                        $_size709 = 0;
                        $_etype712 = 0;
                        $xfer += $input->readListBegin($_etype712, $_size709);
                        for ($_i713 = 0; $_i713 < $_size709; ++$_i713) {
                            $elem714 = null;
                            $xfer += $input->readI64($elem714);
                            $this->txn_ids []= $elem714;
                        }
                        $xfer += $input->readListEnd();
                    } else {
                        $xfer += $input->skip($ftype);
                    }
                    break;
                default:
                    $xfer += $input->skip($ftype);
                    break;
            }
            $xfer += $input->readFieldEnd();
        }
        $xfer += $input->readStructEnd();
        return $xfer;
    }

    public function write($output)
    {
        $xfer = 0;
        $xfer += $output->writeStructBegin('OpenTxnsResponse');
        if ($this->txn_ids !== null) {
            if (!is_array($this->txn_ids)) {
                throw new TProtocolException('Bad type in structure.', TProtocolException::INVALID_DATA);
            }
            $xfer += $output->writeFieldBegin('txn_ids', TType::LST, 1);
            $output->writeListBegin(TType::I64, count($this->txn_ids));
            foreach ($this->txn_ids as $iter715) {
                $xfer += $output->writeI64($iter715);
            }
            $output->writeListEnd();
            $xfer += $output->writeFieldEnd();
        }
        $xfer += $output->writeFieldStop();
        $xfer += $output->writeStructEnd();
        return $xfer;
    }
}
